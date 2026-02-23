package com.project.aiagent.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor  // ✅ Required by JPA
@AllArgsConstructor // ✅ Helpful for testing
@Table(name = "exercises")
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100) // SQL: VARCHAR(100) NOT NULL
    private String name;

    @Column(name = "muscle_group", nullable = false, length = 50) // SQL: VARCHAR(50) NOT NULL
    private String muscleGroup;

    @Column(name = "muscle_sub_group", nullable = false, length = 50) // SQL: VARCHAR(50) NOT NULL
    private String muscleSubGroup;

    @Column(nullable = false, length = 20) // SQL: VARCHAR(20) NOT NULL
    private String intensity;

    @Column(length = 500)
    private String description;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "is_isolation")
    private Boolean isIsolation;
}