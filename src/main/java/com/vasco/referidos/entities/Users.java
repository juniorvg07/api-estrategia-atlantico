package com.vasco.referidos.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "users")
public class Users {
    private String id;
    private String username;
    private String password;
    private String name;
    private String foro;
    private String rol;
}
