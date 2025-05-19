package com.vasco.referidos.controllers;

import com.vasco.referidos.entities.Personal;
import com.vasco.referidos.repositories.PersonalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/personal")
public class PersonalController {
    @Autowired
    private final PersonalRepository personalRepository;

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

    //Llamado general a los referdidos. Para el monitoreo departamental
    @GetMapping("/getReferidos")
    public List<Personal> getAllReferidos() {
        List<Personal> tempList = personalRepository.findAll();
        List<Personal> referidos = new ArrayList<>();

        for (int i = 0; i < tempList.size(); i++){
            if (tempList.get(i).getRol().equals("Referido")){
                referidos.add(tempList.get(i));
            }
        }
        return referidos;
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

    //Llamado a los referidos por foro. Para el monitoreo por templo
    @GetMapping("/getReferidosForo={foro}")
    public List<Personal> getReferidosForo(@PathVariable String foro) {
        List<Personal> tempList = personalRepository.findAll();
        List<Personal> referidosForo = new ArrayList<>();

        for (int i = 0; i < tempList.size(); i++){
            if (tempList.get(i).getForo().equals(foro) && tempList.get(i).getRol().equals("Referido")){
                referidosForo.add(tempList.get(i));
            }
        }
        return referidosForo;
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
