package com.project.aiagent;

import com.project.aiagent.model.DailyStats;
import com.project.aiagent.model.User;
import com.project.aiagent.repository.DailyStatsRepository;
import com.project.aiagent.repository.ExerciseRepository;
import com.project.aiagent.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final DailyStatsRepository dailyStatsRepository;
    private final ExerciseRepository exerciseRepository;

    public DataLoader(UserRepository userRepository,
                      DailyStatsRepository dailyStatsRepository,
                      ExerciseRepository exerciseRepository) {
        this.userRepository = userRepository;
        this.dailyStatsRepository = dailyStatsRepository;
        this.exerciseRepository = exerciseRepository;
    }

    @Override
    public void run(String... args) throws Exception {

        // --- 1. Create a Master User "Jai" if he doesn't exist ---
        if (userRepository.count() == 0) {
            User user = new User();
            user.setName("Jai");
            user.setAge(24);
            user.setWeight(75.5);
            user.setHeight(175.0);
            user.setFitnessGoal("Hypertrophy & Longevity");
            user.setExperienceLevel("Intermediate");
            user.setGender("Male");
            user.setBodyFatPercentage(18.0);
            user.setTargetCalories(2500);

            // ⚠️ AI SAFETY TRIGGER: The AI must read this and ban Deadlifts
            user.setInjuries("Lower Back Pain");
            user.setDietaryPreference("High Protein");

            userRepository.save(user);
            System.out.println("✅ Super User 'Jai' Created!");

            // --- 2. Create today's stats (Simulating a tired day) ---
            DailyStats today = new DailyStats();
            today.setUser(user);
            today.setDate(LocalDate.now());

            // ⚠️ AI INTENSITY TRIGGER: High fatigue means Volume Reduction
            today.setSleepHours(5.5);
            today.setFatigueLevel(8);
            today.setSorenessLevel(6);
            today.setStressLevel(7);
            today.setWaterIntakeLitres(2.0);
            today.setCaloriesConsumed(2000);
            today.setSleepQuality(4);

            dailyStatsRepository.save(today);
            System.out.println("✅ Daily Stats Logged: High Fatigue detected.");
        }

        // Exercise seeding is skipped because you already ran the SQL script.
    }
}