package com.project.aiagent.service;

import com.project.aiagent.model.Exercise;
import com.project.aiagent.repository.ExerciseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class GymAgentService {

    private final RestClient restClient;
    private final ExerciseRepository exerciseRepository;

    @Value("${groq.api.key}")
    private String groqApiKey;

    private final Map<String, List<Map<String, String>>> userMemory = new ConcurrentHashMap<>();
    private final Map<String, String> userActiveFocus = new ConcurrentHashMap<>();

    public GymAgentService(RestClient.Builder builder, ExerciseRepository exerciseRepository) {
        this.restClient = builder.baseUrl("https://api.groq.com/openai/v1/chat/completions").build();
        this.exerciseRepository = exerciseRepository;
    }

    public String getCoaching(String userId, String userMessage) {
        userMemory.putIfAbsent(userId, new ArrayList<>());
        List<Map<String, String>> history = userMemory.get(userId);

        // 1. GUARDRAIL: OFF-TOPIC
        if (isOffTopic(userMessage)) {
            clearUserHistory(userId);
            return "{\"coach_message\": \"I am a Gym Coach. Let's get back to training!\", \"warning_level\": \"RED\", \"routine\": []}";
        }

        // 2. DETECT INTENT & QUANTITY
        boolean isReplacement = userMessage.toLowerCase().contains("replace") ||
                userMessage.toLowerCase().contains("change") ||
                userMessage.toLowerCase().contains("swap");

        int requestedCount = extractNumber(userMessage);
        if (userMessage.toLowerCase().contains("each") && requestedCount > 0) {
            requestedCount = requestedCount * 2;
        }

        String quantityRule = (requestedCount > 0)
                ? "You MUST provide EXACTLY " + requestedCount + " exercises. No more, no less."
                : "Use 4-6 exercises.";

        // 3. DETECT TRAINING FOCUS
        String detectedFocus = detectTrainingFocus(userMessage);
        if (detectedFocus != null) userActiveFocus.put(userId, detectedFocus);
        String currentFocus = userActiveFocus.get(userId);

        // 4. EFFICIENT DATABASE SEARCH
        String lastAiResponse = getLastAiResponse(history);
        List<String> keywords = extractKeywords(userMessage + " " + lastAiResponse, currentFocus);

        String dbContext = "NO EXERCISES FOUND";
        if (!keywords.isEmpty()) {
            List<Exercise> availableExercises = exerciseRepository.findByMultipleKeywords(
                    keywords.stream().map(String::toLowerCase).collect(Collectors.toList()),
                    userMessage.toLowerCase()
            );

            if (!availableExercises.isEmpty()) {
                dbContext = availableExercises.stream()
                        .distinct()
                        .map(e -> e.getName() + " [Target: " + e.getMuscleSubGroup() + "] [Type: " + (Boolean.TRUE.equals(e.getIsIsolation()) ? "Isolation" : "Compound") + "]")
                        .collect(Collectors.joining(", "));
            }
        }

        // 5. PROMPT GENERATION
        String jsonSchema = "{\"coach_message\": \"String\", \"warning_level\": \"String\", \"routine\": [ { \"exercise\": \"String\", \"sets\": \"String\", \"reps\": \"String\", \"notes\": \"String\" } ]}";

        String systemPrompt = isReplacement ?
                "You are a SURGICAL replacement engine. Swap: \"%s\". DB: %s. Rule: Return EXACTLY ONE exercise card. JSON: %s".formatted(userMessage, dbContext, jsonSchema) :
                "You are GymFlow, an elite Coach. DB: %s. FOCUS: %s. RULES: 1. %s 2. %s 3. If focus is ARMS, balance Bicep/Tricep evenly. 4. Keep coach_message brief. JSON: %s"
                        .formatted(dbContext, currentFocus != null ? currentFocus : "GENERAL", getLogicInstruction(currentFocus), quantityRule, jsonSchema);

        // 6. CALL API
        try {
            var response = restClient.post()
                    .header("Authorization", "Bearer " + groqApiKey)
                    .body(Map.of(
                            "model", "llama-3.1-8b-instant",
                            "messages", buildMessages(systemPrompt, isReplacement ? Collections.emptyList() : history, userMessage),
                            "response_format", Map.of("type", "json_object")
                    ))
                    .retrieve().body(Map.class);

// 1. Get the list of choices
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");

// 2. Get the first choice map
            Map<String, Object> firstChoice = choices.get(0);

// 3. Get the message map from that choice
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");


            String aiResponse = (String) message.get("content");
            history.add(Map.of("role", "user", "content", userMessage));
            history.add(Map.of("role", "assistant", "content", aiResponse));
            if (history.size() > 10) history.subList(0, 2).clear();
            return aiResponse;
        } catch (Exception e) {
            return "{\"coach_message\": \"System Error: " + e.getMessage() + "\", \"warning_level\": \"RED\", \"routine\": []}";
        }
    }

    private int extractNumber(String msg) {
        Matcher m = Pattern.compile("\\d+").matcher(msg);
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }

    private boolean isOffTopic(String msg) {
        String lower = msg.toLowerCase();
        if (lower.contains("punching bag") || lower.contains("heavy bag")) return false;
        return List.of("buy", "price", "dating", "weather", "politics", "code", "java").stream().anyMatch(lower::contains);
    }

    private String detectTrainingFocus(String msg) {
        String lower = msg.toLowerCase();
        if (lower.contains("arm") || lower.contains("bicep") || lower.contains("tricep")) return "ARMS";
        if (lower.contains("leg") || lower.contains("squat")) return "LEGS";
        if (lower.contains("chest")) return "CHEST";
        if (lower.contains("back")) return "BACK";
        if (lower.contains("shoulder")) return "SHOULDERS";
        if (lower.contains("abs") || lower.contains("core")) return "ABS";
        if (lower.contains("full") || lower.contains("whole")) return "FULL_BODY";
        return null;
    }

    private List<String> extractKeywords(String msg, String focus) {
        Set<String> keywords = new HashSet<>();
        if (focus != null) {
            keywords.add(focus);
            keywords.addAll(getFallbackKeywords(focus));
        }
        if (msg.contains("squat")) keywords.add("SQUAT");
        if (msg.contains("bench")) keywords.add("BENCH");
        if (msg.contains("curl")) keywords.add("CURL");
        return new ArrayList<>(keywords);
    }

    private List<String> getFallbackKeywords(String focus) {
        switch (focus) {
            case "ARMS": return List.of("BICEPS", "TRICEPS", "FOREARMS");
            case "LEGS": return List.of("QUADS", "HAMSTRINGS", "CALVES");
            case "FULL_BODY": return List.of("CHEST", "BACK", "LEGS", "SHOULDERS");
            default: return List.of(focus);
        }
    }

    private String getLogicInstruction(String focus) {
        if (focus == null) return "Create a logical workout.";
        return "Focus: " + focus + ". Ensure a mix of heavy compound and isolation moves.";
    }

    private String getLastAiResponse(List<Map<String, String>> history) {
        return history.stream().filter(m -> "assistant".equals(m.get("role"))).map(m -> m.get("content")).reduce((first, second) -> second).orElse("");
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