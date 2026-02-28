package com.project.aiagent.repository;

import com.project.aiagent.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Allows Spring Security to find the athlete's account when they log in
    Optional<User> findByEmail(String email);

    // Checks if an email is already in the database to prevent duplicate registrations
    boolean existsByEmail(String email);
}