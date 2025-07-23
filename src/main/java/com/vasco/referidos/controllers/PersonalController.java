package com.vasco.referidos.controllers;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.vasco.referidos.entities.Personal;
import com.vasco.referidos.repositories.PersonalRepository;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/personal")
public class PersonalController {
    @Autowired
    private final PersonalRepository personalRepository;
    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    public PersonalController(PersonalRepository personalRepository){
        this.personalRepository = personalRepository;
    }

    //Llamado general a todo el personal
    @GetMapping
    public List<Personal> getAllPersonal() {
        return personalRepository.findAll();
    }

    //Llamado general a los líderes. Para el monitoreo departamental
    @GetMapping("/getLideres")
    public List<Personal> getAllLideres() {
        List<Personal> tempList = personalRepository.findAll();
        List<Personal> lideres = new ArrayList<>();

        for (int i = 0; i < tempList.size(); i++){
            if (tempList.get(i).getRol().equals("Líder")){
                lideres.add(tempList.get(i));
            }
        }
        return lideres;
    }

    @GetMapping("/getReferidosWithLider")
    public List<Document> getReferidosConLider() {
        MatchOperation match = Aggregation.match(Criteria.where("rol").is("Referido"));

        // $lookup con $expr para convertir idLider (string) a ObjectId
        Document lookupExpr = new Document("$lookup",
                new Document("from", "respuestas")
                        .append("let", new Document("idLiderStr", "$lider"))
                        .append("pipeline", List.of(
                                new Document("$match", new Document("$expr", new Document("$eq", List.of("$_id", new Document("$toObjectId", "$$idLiderStr"))))),
                                new Document("$project", new Document("nombre", 1).append("documento", 1))
                        ))
                        .append("as", "datosLider")
        );

        // Crear operación personalizada con $lookup complejo
        AggregationOperation customLookup = context -> lookupExpr;

        UnwindOperation unwind = Aggregation.unwind("datosLider", true);

        AddFieldsOperation addNombreLider = AddFieldsOperation.addField("nombreLider")
                .withValue(Fields.field("datosLider.nombre"))
                .build();

        AddFieldsOperation addDocumentoLider = AddFieldsOperation.addField("documentoLider")
                .withValue(Fields.field("datosLider.documento"))
                .build();

        Aggregation aggregation = Aggregation.newAggregation(
                match,
                customLookup,
                unwind,
                addNombreLider,
                addDocumentoLider
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, "respuestas", Document.class);

        return results.getMappedResults();
    }

    //Metodo para obtener los datos para la vista de arbol del Rol SUPERADMIN
    @GetMapping("/getAllDataToTree")
    public ResponseEntity<List<Map<String, Object>>> getAllDataToTree() {
        List<Bson> pipeline = Arrays.asList(
                // Paso 1: Crear liderObjectId si el campo lider es válido
                new Document("$addFields", new Document("liderObjectId", new Document("$cond", Arrays.asList(
                        new Document("$and", Arrays.asList(
                                new Document("$ne", Arrays.asList("$lider", null)),
                                new Document("$ne", Arrays.asList("$lider", "")),
                                new Document("$eq", Arrays.asList(new Document("$type", "$lider"), "string"))
                        )),
                        new Document("$toObjectId", "$lider"),
                        null
                )))),

                // Paso 2: Lookup para obtener datos del líder
                new Document("$lookup", new Document()
                        .append("from", "respuestas")
                        .append("localField", "liderObjectId")
                        .append("foreignField", "_id")
                        .append("as", "datosLiderRaw")
                ),

                // Paso 3: Unwind
                new Document("$unwind", new Document("path", "$datosLiderRaw").append("preserveNullAndEmptyArrays", true)),

                // Paso 4: Crear campo datosLider solo si es Referido
                new Document("$addFields", new Document("datosLider", new Document("$cond", Arrays.asList(
                        new Document("$and", Arrays.asList(
                                new Document("$eq", Arrays.asList("$rol", "Referido")),
                                new Document("$ne", Arrays.asList("$datosLiderRaw", null))
                        )),
                        new Document("documento", "$datosLiderRaw.documento")
                                .append("nombre", new Document("$concat", Arrays.asList("$datosLiderRaw.nombre", " ", "$datosLiderRaw.apellido"))),
                        null
                )))),

                // Paso 5: Proyección
                new Document("$project", new Document()
                        .append("documento", 1)
                        .append("nombre", 1)
                        .append("apellido", 1)
                        .append("rol", 1)
                        .append("datosLider", 1)
                )
        );

        // Ejecutar manualmente la agregación
        List<Map<String, Object>> resultados = new ArrayList<>();
        MongoCollection<Document> collection = mongoTemplate.getCollection("respuestas");

        try (MongoCursor<Document> cursor = collection.aggregate(pipeline).iterator()) {
            while (cursor.hasNext()) {
                resultados.add(cursor.next());
            }
        }

        return ResponseEntity.ok(resultados);
    }

    @PostMapping("/getCantidadLideres")
    public List<Document> getLideresByForo(@RequestBody List<String> foros){
        MatchOperation match = Aggregation.match(Criteria.where("rol").is("Líder").and("foro").in(foros));

        GroupOperation group = Aggregation.group("foro").count().as("cantidad");

        ProjectionOperation project = Aggregation.project()
                .and("cantidad").as("cantidad")
                .and("_id").as("foro")
                .andExclude("_id");

        Aggregation aggregation = Aggregation.newAggregation(match, group, project);

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, "respuestas", Document.class);

        // 2. Convertimos los resultados a un Map para fácil acceso
        Map<String, Integer> foroCantidadMap = new HashMap<>();
        for (Document doc : results) {
            foroCantidadMap.put(doc.getString("foro"), doc.getInteger("cantidad"));
        }

        // 3. Recorremos todos los foros solicitados y agregamos 0 donde no existan
        List<Document> finalResults = new ArrayList<>();
        for (String foro : foros) {
            int cantidad = foroCantidadMap.getOrDefault(foro, 0);
            Document item = new Document("foro", foro).append("cantidad", cantidad);
            finalResults.add(item);
        }

        return finalResults;
    }

    @PostMapping("/getCantidadReferidos")
    public List<Document> getReferidosByForo(@RequestBody List<String> foros){
        MatchOperation match = Aggregation.match(Criteria.where("rol").is("Referido").and("foro").in(foros));

        GroupOperation group = Aggregation.group("foro").count().as("cantidad");

        ProjectionOperation project = Aggregation.project()
                .and("cantidad").as("cantidad")
                .and("_id").as("foro")
                .andExclude("_id");

        Aggregation aggregation = Aggregation.newAggregation(match, group, project);

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, "respuestas", Document.class);

        // 2. Convertimos los resultados a un Map para fácil acceso
        Map<String, Integer> foroCantidadMap = new HashMap<>();
        for (Document doc : results) {
            foroCantidadMap.put(doc.getString("foro"), doc.getInteger("cantidad"));
        }

        // 3. Recorremos todos los foros solicitados y agregamos 0 donde no existan
        List<Document> finalResults = new ArrayList<>();
        for (String foro : foros) {
            int cantidad = foroCantidadMap.getOrDefault(foro, 0);
            Document item = new Document("foro", foro).append("cantidad", cantidad);
            finalResults.add(item);
        }

        return finalResults;
    }

    //Llamado a los líderes por foro. Para el monitoreo por templo
    @GetMapping("/getLideresForo={foro}")
    public List<Personal> getLideresForo(@PathVariable String foro) {
        List<Personal> tempList = personalRepository.findAll();
        List<Personal> lideresForo = new ArrayList<>();

        for (int i = 0; i < tempList.size(); i++){
            if (tempList.get(i).getForo().equals(foro) && tempList.get(i).getRol().equals("Líder")){
                lideresForo.add(tempList.get(i));
            }
        }
        return lideresForo;
    }

    @GetMapping("/getReferidosForoWithLider={foro}")
    public List<Document> getReferidosConLiderPorForo(@PathVariable String foro) {
        MatchOperation match = Aggregation.match(Criteria.where("rol").is("Referido").and("foro").is(foro));

        // $lookup con $expr para convertir idLider (string) a ObjectId
        Document lookupExpr = new Document("$lookup",
                new Document("from", "respuestas")
                        .append("let", new Document("idLiderStr", "$lider"))
                        .append("pipeline", List.of(
                                new Document("$match", new Document("$expr", new Document("$eq", List.of("$_id", new Document("$toObjectId", "$$idLiderStr"))))),
                                new Document("$project", new Document("nombre", 1).append("documento", 1))
                        ))
                        .append("as", "datosLider")
        );

        // Crear operación personalizada con $lookup complejo
        AggregationOperation customLookup = context -> lookupExpr;

        UnwindOperation unwind = Aggregation.unwind("datosLider", true);

        AddFieldsOperation addNombreLider = AddFieldsOperation.addField("nombreLider")
                .withValue(Fields.field("datosLider.nombre"))
                .build();

        AddFieldsOperation addDocumentoLider = AddFieldsOperation.addField("documentoLider")
                .withValue(Fields.field("datosLider.documento"))
                .build();

        Aggregation aggregation = Aggregation.newAggregation(
                match,
                customLookup,
                unwind,
                addNombreLider,
                addDocumentoLider
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, "respuestas", Document.class);

        return results.getMappedResults();
    }

    //Datos para el arbol por foro del rol ADMIN
    @GetMapping("/getDataTreeForo={foro}")
    public ResponseEntity<List<Map<String, Object>>> getDataTreeForo(@PathVariable String foro) {
        List<Bson> pipeline = Arrays.asList(
                // Paso 0: Filtrar por el campo 'foro'
                new Document("$match", new Document("foro", foro)),

                // Paso 1: Crear liderObjectId si el campo lider es válido
                new Document("$addFields", new Document("liderObjectId", new Document("$cond", Arrays.asList(
                        new Document("$and", Arrays.asList(
                                new Document("$ne", Arrays.asList("$lider", null)),
                                new Document("$ne", Arrays.asList("$lider", "")),
                                new Document("$eq", Arrays.asList(new Document("$type", "$lider"), "string"))
                        )),
                        new Document("$toObjectId", "$lider"),
                        null
                )))),

                // Paso 2: Lookup para obtener datos del líder
                new Document("$lookup", new Document()
                        .append("from", "respuestas")
                        .append("localField", "liderObjectId")
                        .append("foreignField", "_id")
                        .append("as", "datosLiderRaw")
                ),

                // Paso 3: Unwind
                new Document("$unwind", new Document("path", "$datosLiderRaw").append("preserveNullAndEmptyArrays", true)),

                // Paso 4: Crear campo datosLider solo si es Referido
                new Document("$addFields", new Document("datosLider", new Document("$cond", Arrays.asList(
                        new Document("$and", Arrays.asList(
                                new Document("$eq", Arrays.asList("$rol", "Referido")),
                                new Document("$ne", Arrays.asList("$datosLiderRaw", null))
                        )),
                        new Document("documento", "$datosLiderRaw.documento")
                                .append("nombre", new Document("$concat", Arrays.asList("$datosLiderRaw.nombre", " ", "$datosLiderRaw.apellido"))),
                        null
                )))),

                // Paso 5: Proyección
                new Document("$project", new Document()
                        .append("documento", 1)
                        .append("nombre", 1)
                        .append("apellido", 1)
                        .append("rol", 1)
                        .append("datosLider", 1)
                )
        );

        // Ejecutar manualmente la agregación
        List<Map<String, Object>> resultados = new ArrayList<>();
        MongoCollection<Document> collection = mongoTemplate.getCollection("respuestas");

        try (MongoCursor<Document> cursor = collection.aggregate(pipeline).iterator()) {
            while (cursor.hasNext()) {
                resultados.add(cursor.next());
            }
        }

        return ResponseEntity.ok(resultados);
    }

    //Llamado a los líderes de un cuidador. Para el monitoreo de un cuidador específico
    @GetMapping("/getLideresUser={user}")
    public List<Personal> getLideresUser(@PathVariable String user) {
        List<Personal> tempList = personalRepository.findAll();
        List<Personal> lideresUser = new ArrayList<>();

        for (int i = 0; i < tempList.size(); i++){
            if (tempList.get(i).getCreated_by().equals(user) && tempList.get(i).getRol().equals("Líder")){
                lideresUser.add(tempList.get(i));
            }
        }
        return lideresUser;
    }

    //Consulta de Referidos para el monitoreo del Rol Users
    @GetMapping("/getReferidosByUser={user}")
    public List<Document> getReferidosByUser(@PathVariable String user) {
        MatchOperation match = Aggregation.match(Criteria.where("rol").is("Referido").and("created_by").is(user));

        // $lookup con $expr para convertir idLider (string) a ObjectId
        Document lookupExpr = new Document("$lookup",
                new Document("from", "respuestas")
                        .append("let", new Document("idLiderStr", "$lider"))
                        .append("pipeline", List.of(
                                new Document("$match", new Document("$expr", new Document("$eq", List.of("$_id", new Document("$toObjectId", "$$idLiderStr"))))),
                                new Document("$project", new Document("nombre", 1).append("documento", 1))
                        ))
                        .append("as", "datosLider")
        );

        // Crear operación personalizada con $lookup complejo
        AggregationOperation customLookup = context -> lookupExpr;

        UnwindOperation unwind = Aggregation.unwind("datosLider", true);

        AddFieldsOperation addNombreLider = AddFieldsOperation.addField("nombreLider")
                .withValue(Fields.field("datosLider.nombre"))
                .build();

        AddFieldsOperation addDocumentoLider = AddFieldsOperation.addField("documentoLider")
                .withValue(Fields.field("datosLider.documento"))
                .build();

        Aggregation aggregation = Aggregation.newAggregation(
                match,
                customLookup,
                unwind,
                addNombreLider,
                addDocumentoLider
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, "respuestas", Document.class);

        return results.getMappedResults();
    }

    @GetMapping("/getDataTreeByUser={createdBy}")
    public List<Document> getDataTreeByUser(@PathVariable String createdBy) {
        // 1. Filtro por created_by
        MatchOperation matchCreatedBy = Aggregation.match(Criteria.where("created_by").is(createdBy));

        // 2. Lookup para referidos (condicional)
        Document lookup = new Document("$lookup",
                new Document("from", "respuestas")
                        .append("let", new Document("idLiderStr", "$lider"))
                        .append("pipeline", List.of(
                                new Document("$match",
                                        new Document("$expr",
                                                new Document("$eq", List.of("$_id", new Document("$toObjectId", "$$idLiderStr")))
                                        )
                                ),
                                new Document("$project",
                                        new Document("documento", 1)
                                                .append("nombreCompleto", new Document("$concat", List.of("$nombre", " ", "$apellido")))
                                )
                        ))
                        .append("as", "datosLiderRaw")
        );

        // 3. Desenrollar datos del líder (si existen)
        UnwindOperation unwind = Aggregation.unwind("datosLiderRaw", true);

        // 4. Crear campo datosLider dependiendo del rol
        Document addDatosLider = new Document("$addFields",
                new Document("datosLider", new Document("$cond", List.of(
                        new Document("$and", List.of(
                                new Document("$eq", List.of("$rol", "Referido")),
                                new Document("$gt", List.of(new Document("$type", "$datosLiderRaw"), "missing"))
                        )),
                        new Document("documento", "$datosLiderRaw.documento")
                                .append("nombre", "$datosLiderRaw.nombreCompleto"),
                        null
                )))
        );

        // 5. Seleccionar campos finales
        ProjectionOperation project = Aggregation.project("documento", "nombre", "apellido", "rol", "datosLider");

        // 6. Ejecutar agregación
        Aggregation aggregation = Aggregation.newAggregation(
                matchCreatedBy,
                context -> lookup,
                unwind,
                context -> addDatosLider,
                project
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, "respuestas", Document.class);

        return results.getMappedResults();
    }

    //Buscar una persona por documento
    @GetMapping("/buscar")
    public Personal getPersona(@RequestParam String documento) {
        return personalRepository.findByDocumento(documento).orElse(null);
    }

    //Buscar una persona por ID. Para ubicar al líder con su respectivo referido
    @GetMapping("/buscarLiderById")
    public Personal getLider(@RequestParam String id) {
        return personalRepository.findById(id).orElse(null);
    }

    @PostMapping("/newPerson")
    public Personal crearPersonal(@RequestBody Personal personal) {
        return personalRepository.save(personal);
    }

    @PutMapping("/updatePerson")
    public Personal actualizarPersonal(@RequestBody Personal personal) {
        return personalRepository.save(personal);
    }

    @DeleteMapping
    public void deletePerson(@RequestParam String id){
        personalRepository.deleteById(id);
    }
}
