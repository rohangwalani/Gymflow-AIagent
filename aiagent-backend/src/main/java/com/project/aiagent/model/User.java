package com.project.aiagent.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

@Entity
@Data
@Table(name = "gym_users") // Avoid SQL reserved keyword "User"
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Authentication Data (NEW) ---
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable=true)
    private String password;

    @Column(nullable = false)
    private boolean enabled = false; // For email verification

    // --- Identity ---
    private String name;
    private int age;
    private String gender; // "Male", "Female", "Other"

    // --- Biometrics ---
    private double weight; // kg
    private double height; // cm
    private double bodyFatPercentage; // Optional, can be null

    // --- The Coach's Context ---
    private String fitnessGoal; // e.g., "Powerlifting", "Marathon", "Rehab"
    private String experienceLevel; // "Beginner", "Intermediate", "Elite"

    // --- The Safety Filter ---
    @Column(length = 500)
    private String injuries; // e.g., "Herniated Disc L5, Rotator Cuff"

    // --- The Fuel System ---
    private String dietaryPreference; // "Vegan", "Keto", "None"
    private int targetCalories; // Calculated TDEE

    // Relationship: One User has many Daily Stats entries
//    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
//    private List<DailyStats> dailyStatsLogs;
}