package com.project.aiagent.service;

import com.project.aiagent.model.Exercise;
import com.project.aiagent.repository.ExerciseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class GymAgentService {

    private final RestClient restClient;
    private final ExerciseRepository exerciseRepository;

    @Value("${groq.api.key}")
    private String groqApiKey;

    // MEMORY STORE
    private final Map<String, List<Map<String, String>>> userMemory = new ConcurrentHashMap<>();
    private final Map<String, String> userActiveFocus = new ConcurrentHashMap<>();

    public GymAgentService(RestClient.Builder builder, ExerciseRepository exerciseRepository) {
        this.restClient = builder.baseUrl("https://api.groq.com/openai/v1/chat/completions").build();
        this.exerciseRepository = exerciseRepository;
    }

    public String getCoaching(String userId, String userMessage) {

        // 1. INIT MEMORY
        userMemory.putIfAbsent(userId, new ArrayList<>());
        List<Map<String, String>> history = userMemory.get(userId);

        // 2. GUARDRAIL: DETECT OFF-TOPIC INPUT (The Fix)
        if (isOffTopic(userMessage)) {
            // Clear context so we don't get stuck in a weird loop
            clearUserHistory(userId);
            return """
                {
                  "coach_message": "I am a Gym Coach. I cannot help with shopping, relationships, or general advice. Let's get back to training!",
                  "warning_level": "RED",
                  "routine": []
                }
                """;
        }

        // 3. DETECT INTENT
        boolean isReplacement = userMessage.toLowerCase().contains("replace") ||
                userMessage.toLowerCase().contains("change") ||
                userMessage.toLowerCase().contains("swap");

        // 4. DETECT TRAINING FOCUS
        String detectedFocus = detectTrainingFocus(userMessage);
        if (detectedFocus != null) {
            userActiveFocus.put(userId, detectedFocus);
        }

        // 5. SMART SEARCH
        String lastAiResponse = getLastAiResponse(history);
        String searchContext = userMessage + " " + lastAiResponse;
        String currentFocus = userActiveFocus.get(userId);

        List<String> searchKeywords = extractKeywords(searchContext, currentFocus);

        // Fallback: Only use previous context if the user is NOT asking for something completely new
        if (searchKeywords.isEmpty() && currentFocus != null && !isReplacement) {
            searchKeywords.addAll(getFallbackKeywords(currentFocus));
        }

        // 6. FETCH DATABASE
        String dbContext = "NO EXERCISES FOUND";
        if (!searchKeywords.isEmpty()) {
            List<Exercise> availableExercises = new ArrayList<>();
            for (String keyword : searchKeywords) {
                availableExercises.addAll(exerciseRepository.searchExercises(keyword));
            }
            if (!availableExercises.isEmpty()) {
                dbContext = availableExercises.stream()
                        .distinct()
                        .map(e -> {
                            boolean isIso = Boolean.TRUE.equals(e.getIsIsolation());
                            String type = isIso ? "Isolation" : "Compound";
                            String target = (e.getMuscleSubGroup() != null) ? e.getMuscleSubGroup() : "General";
                            return e.getName() + " [Target: " + target + "] [Type: " + type + "]";
                        })
                        .collect(Collectors.joining(", "));
            }
        }

        // 7. DYNAMIC SYSTEM PROMPT
        String jsonSchema = """
            {
              "coach_message": "String",
              "warning_level": "String",
              "routine": [ { "exercise": "String", "sets": "String", "reps": "String", "notes": "String" } ]
            }
            """;

        String systemPrompt;

        if (isReplacement) {
            // --- SNIPER MODE ---
            systemPrompt = """
                You are a SURGICAL replacement engine.
                User wants to swap: "%s"
                **DATABASE:** %s
                **STRICT RULES:**
                1. Find ONE valid substitute.
                2. Do NOT return the full workout list.
                3. The 'routine' array must contain EXACTLY ONE exercise.
                **OUTPUT JSON:** %s
                """.formatted(userMessage, dbContext, jsonSchema);
        } else {
            // --- GENERATOR MODE ---
            String logicInstruction = getLogicInstruction(currentFocus);
            systemPrompt = """
                You are GymFlow, an elite Sports & Strength Coach.
                **DATABASE:** %s
                **FOCUS:** %s
                **RULES:**
                %s
                2. Use 4-6 exercises.
                3. **GUARDRAIL:** If the user asks about shopping, weather, or non-fitness topics, return an empty routine and a polite refusal.
                **OUTPUT JSON:** %s
                """.formatted(dbContext, currentFocus != null ? currentFocus : "GENERAL", logicInstruction, jsonSchema);
        }

        // 8. CALL API
        try {
            var requestBody = Map.of(
                    "model", "llama-3.1-8b-instant",
                    "messages", buildMessages(systemPrompt, isReplacement ? Collections.emptyList() : history, userMessage),
                    "response_format", Map.of("type", "json_object")
            );

            var response = restClient.post()
                    .header("Authorization", "Bearer " + groqApiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            var choices = (List<Map<String, Object>>) response.get("choices");
            var messageObj = (Map<String, Object>) choices.get(0).get("message");
            String aiResponse = (String) messageObj.get("content");

            history.add(Map.of("role", "user", "content", userMessage));
            history.add(Map.of("role", "assistant", "content", aiResponse));
            if (history.size() > 10) history.subList(0, 2).clear();

            return aiResponse;

        } catch (Exception e) {
            return "{\"coach_message\": \"System Error: " + e.getMessage() + "\", \"warning_level\": \"RED\", \"routine\": []}";
        }
    }

    // --- INTELLIGENCE METHODS ---

    private boolean isOffTopic(String msg) {
        msg = msg.toLowerCase();
        List<String> forbidden = List.of(
                "buy", "sell", "price", "cost", "wallet", "bag", "purse", "fashion", "shopping", // Shopping
                "relationship", "dating", "love", "divorce", "kiss", "marry",                    // Relationships
                "weather", "news", "politics", "movie", "song", "game",                          // General
                "code", "java", "python", "debug"                                                // Coding
        );

        // Exception: "Punching bag" or "Sand bag" is allowed
        if (msg.contains("punching bag") || msg.contains("heavy bag") || msg.contains("sand bag")) return false;

        return forbidden.stream().anyMatch(msg::contains);
    }

    private String detectTrainingFocus(String msg) {
        msg = msg.toLowerCase();

        // 1. SPORTS
        if (msg.contains("football") || msg.contains("soccer") || msg.contains("running") || msg.contains("sprint")) return "SPORT_RUNNING_BASED";
        if (msg.contains("basketball") || msg.contains("volleyball") || msg.contains("jump")) return "SPORT_JUMPING_BASED";
        if (msg.contains("tennis") || msg.contains("cricket") || msg.contains("baseball") || msg.contains("golf") || msg.contains("throw")) return "SPORT_ROTATIONAL";
        if (msg.contains("swim")) return "SPORT_SWIMMING";
        if (msg.contains("fight") || msg.contains("boxing") || msg.contains("mma")) return "SPORT_COMBAT";

        // 2. FUNCTIONAL / LIFE
        if (msg.contains("posture") || msg.contains("sit") || msg.contains("desk")) return "FUNC_POSTURE";
        if (msg.contains("carry") || msg.contains("grocery") || msg.contains("grip")) return "FUNC_STRENGTH";
        if (msg.contains("stamina") || msg.contains("endurance") || msg.contains("sex")) return "CARDIO"; // Mapped user request

        // 3. MOBILITY / RECOVERY
        if (msg.contains("yoga") || msg.contains("stretch") || msg.contains("flexible") || msg.contains("mobility")) return "MOBILITY";

        // 4. GENERAL GYM
        if (msg.contains("full") || msg.contains("whole") || msg.contains("mix")) return "FULL_BODY";
        if (msg.contains("upper")) return "UPPER_BODY";
        if (msg.contains("lower")) return "LOWER_BODY";
        if (msg.contains("leg") || msg.contains("squat")) return "LEGS";
        if (msg.contains("chest") || msg.contains("bench")) return "CHEST";
        if (msg.contains("back") || msg.contains("row")) return "BACK";
        if (msg.contains("shoulder")) return "SHOULDERS";
        if (msg.contains("arm") || msg.contains("bicep") || msg.contains("tricep")) return "ARMS";
        if (msg.contains("abs") || msg.contains("core")) return "ABS";
        if (msg.contains("cardio") || msg.contains("stamina")) return "CARDIO";

        return null;
    }

    private List<String> extractKeywords(String msg, String focus) {
        msg = msg.toLowerCase();
        Set<String> keywords = new HashSet<>();

        if (msg.contains("squat")) keywords.add("SQUAT");
        if (msg.contains("deadlift")) keywords.add("DEADLIFT");
        if (msg.contains("bench")) keywords.add("BENCH");

        return new ArrayList<>(keywords);
    }

    private List<String> getFallbackKeywords(String focus) {
        if (focus == null) return new ArrayList<>();
        switch (focus) {
            case "SPORT_RUNNING_BASED": return List.of("LEGS", "CARDIO", "ABS", "LUNGE");
            case "SPORT_JUMPING_BASED": return List.of("LEGS", "CALVES", "SHOULDERS", "SQUAT");
            case "SPORT_ROTATIONAL": return List.of("ABS", "SHOULDERS", "BACK", "ARMS");
            case "SPORT_SWIMMING": return List.of("BACK", "SHOULDERS", "ABS", "LATS");
            case "SPORT_COMBAT": return List.of("SHOULDERS", "ABS", "CARDIO", "HIIT");
            case "FUNC_POSTURE": return List.of("BACK", "ABS", "GLUTES", "FACE PULL");
            case "FUNC_STRENGTH": return List.of("FOREARMS", "BACK", "LEGS", "DEADLIFT");
            case "MOBILITY": return List.of("ABS", "BACK", "LEGS");
            case "FULL_BODY": return List.of("CHEST", "BACK", "LEGS", "SHOULDERS");
            case "UPPER_BODY": return List.of("CHEST", "BACK", "SHOULDERS", "ARMS");
            case "LOWER_BODY": return List.of("LEGS", "ABS");
            case "CARDIO": return List.of("CARDIO", "LEGS", "ABS");
            default: return List.of(focus);
        }
    }

    private String getLogicInstruction(String focus) {
        if (focus == null) return "1. Create a logical workout.";
        return switch (focus) {
            case "SPORT_RUNNING_BASED" -> "1. Design for SPEED & ENDURANCE. Mix Unilateral Legs + Core + Cardio.";
            case "SPORT_JUMPING_BASED" -> "1. Design for EXPLOSIVE POWER. Heavy Squats + Calf work + Shoulder stability.";
            case "SPORT_ROTATIONAL" -> "1. Design for ROTATIONAL POWER. Core (Woodchoppers) + Shoulders + Lats.";
            case "SPORT_SWIMMING" -> "1. Design for UPPER BODY ENDURANCE. Lats + Shoulders + Core stability.";
            case "FUNC_POSTURE" -> "1. Design for POSTURE CORRECTION. Focus on Rear Delts, Upper Back, and Core.";
            case "MOBILITY" -> "1. Design for FLEXIBILITY. Focus on full range of motion exercises.";
            case "FULL_BODY" -> "1. Full Body: Pick 1 Chest, 1 Back, 1 Leg, 1 Shoulder, 1 Core.";
            case "CARDIO" -> "1. Endurance Focus: Mix Cardio machines with high-rep bodyweight moves.";
            default -> "1. Create a logical hypertrophy workout (Heavy -> Light).";
        };
    }

    private String getLastAiResponse(List<Map<String, String>> history) {
        if (history.isEmpty()) return "";
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("assistant".equals(history.get(i).get("role"))) return history.get(i).get("content");
        }
        return "";
    }

    private List<Map<String, String>> buildMessages(String system, List<Map<String, String>> history, String userMsg) {
        List<Map<String, String>> list = new ArrayList<>();
        list.add(Map.of("role", "system", "content", system));
        list.addAll(history);
        list.add(Map.of("role", "user", "content", userMsg));
        return list;
    }

    public void clearUserHistory(String userId) {
        userMemory.remove(userId);
        userActiveFocus.remove(userId);
    }
}