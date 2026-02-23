package com.project.aiagent.repository;

import com.project.aiagent.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    // We will use findById(1L) for now to simulate the logged-in user
}