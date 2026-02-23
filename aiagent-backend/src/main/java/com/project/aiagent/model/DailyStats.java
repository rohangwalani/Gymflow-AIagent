package com.project.aiagent.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Entity
@Data
public class DailyStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate date; // Today's date

    // --- Recovery Metrics (1-10 Scale) ---
    private double sleepHours;
    private int sleepQuality; // 1 (Terrible) - 10 (Comatose)
    private int fatigueLevel; // 1 (Fresh) - 10 (Exhausted)
    private int sorenessLevel; // DOMS level
    private int stressLevel;  // Mental stress

    // --- Fuel Tracking ---
    private int caloriesConsumed;
    private double waterIntakeLitres;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
}