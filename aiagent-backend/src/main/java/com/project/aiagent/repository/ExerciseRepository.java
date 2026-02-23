package com.project.aiagent.repository;

import com.project.aiagent.model.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param; // ✅ Added Import
import java.util.List;

public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    // 1. BROAD SEARCH (Used by AI to find context)
    @Query("SELECT e FROM Exercise e WHERE " +
            "LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(e.muscleGroup) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(e.muscleSubGroup) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Exercise> searchExercises(@Param("keyword") String keyword); // ✅ Added @Param

    // 2. PRECISE LOOKUP (Optional but powerful)
    // This allows the AI to strictly ask for "Hamstrings" + "Isolation" (True/False)
    List<Exercise> findByMuscleSubGroupAndIsIsolation(String muscleSubGroup, Boolean isIsolation);
}