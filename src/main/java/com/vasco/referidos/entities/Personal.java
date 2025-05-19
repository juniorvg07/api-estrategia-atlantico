package com.vasco.referidos.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "respuestas")
public class Personal {
    private String id;
    private String documento;
    private String nombre;
    private String apellido;
    private String sexo;
    private String edad;
    private String rol;
    private String lider;
    private String foro;
    private String departamento;
    private String municipio;
    private String barrio;
    private String departamento_votacion;
    private String municipio_votacion;
    private String puesto_votacion;
    private String mesa_votacion;
    private String created_by;
}
