package com.vasco.referidos.repositories;

import com.vasco.referidos.entities.Users;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UsersRepository extends MongoRepository<Users, String> {
    Optional<Users> findByUsername(String username);

    Optional<Users> deleteByUsername(String username);
}
