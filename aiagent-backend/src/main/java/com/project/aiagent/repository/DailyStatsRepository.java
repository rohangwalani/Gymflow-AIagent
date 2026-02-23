package com.project.aiagent.repository;

import com.project.aiagent.model.DailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface DailyStatsRepository extends JpaRepository<DailyStats, Long> {
    Optional<DailyStats> findByUserIdAndDate(Long userId, LocalDate date);
}