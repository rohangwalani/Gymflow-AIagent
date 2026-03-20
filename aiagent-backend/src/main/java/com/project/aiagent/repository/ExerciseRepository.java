package com.project.aiagent.repository;

import com.project.aiagent.model.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    /**
     * ADVANCED MULTI-KEYWORD SEARCH
     * Finds exercises where muscle group or subgroup matches the keyword list,
     * OR the name matches the user's specific search term.
     */
    @Query("SELECT DISTINCT e FROM Exercise e WHERE " +
            "LOWER(e.muscleGroup) IN :keywords OR " +
            "LOWER(e.muscleSubGroup) IN :keywords OR " +
            "LOWER(e.name) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Exercise> findByMultipleKeywords(
            @Param("keywords") List<String> keywords,
            @Param("searchTerm") String searchTerm
    );

    // Legacy support for single keyword search
    @Query("SELECT e FROM Exercise e WHERE " +
            "LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(e.muscleGroup) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(e.muscleSubGroup) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Exercise> searchExercises(@Param("keyword") String keyword);

    List<Exercise> findByMuscleSubGroupAndIsIsolation(String muscleSubGroup, Boolean isIsolation);
}