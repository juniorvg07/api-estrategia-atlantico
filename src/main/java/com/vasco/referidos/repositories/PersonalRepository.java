package com.vasco.referidos.repositories;

import com.vasco.referidos.entities.Personal;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PersonalRepository extends MongoRepository<Personal, String> {
    Optional<Personal> findByDocumento(String documento);
}
