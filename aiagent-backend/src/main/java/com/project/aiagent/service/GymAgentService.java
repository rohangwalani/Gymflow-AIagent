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

        // 5. HYPER-FACTUAL PROMPT GENERATION (Zero-Hallucination Design)
        String jsonSchema = "{\"coach_message\": \"String\", \"warning_level\": \"String\", \"routine\": [ { \"exercise\": \"String\", \"sets\": \"String\", \"reps\": \"String\", \"notes\": \"String\" } ]}";

        String systemPrompt;
        if (isReplacement) {
            systemPrompt = """
                You are GymFlow's SURGICAL Workout Modification Engine. Your job is to process an exercise replacement with zero errors.
                
                USER REPLACEMENT REQUEST: "%s"
                AVAILABLE DATABASE EXERCISES: [%s]
                
                CRITICAL OPERATIONAL RULES:
                1. CONTEXT AUDIT: Examine the chat history to see the current active workout routine.
                2. ANATOMICAL MATCHING: The replacement exercise MUST target the exact same muscle group or function as the exercise being removed, unless the user explicitly stated otherwise.
                3. ANTI-DUPLICATION RULE: Review all active exercises in the chat history. You are strictly FORBIDDEN from choosing an exercise that is already present in the current routine.
                4. OUTPUT CONSTRAINT: Return EXACTLY ONE exercise object inside the 'routine' array.
                5. STICK TO THE DATABASE: Only use exercises provided in the AVAILABLE DATABASE string above.
                
                OUTPUT FORMAT:
                You must output ONLY a valid JSON object matching this schema. No conversational prose before or after the JSON.
                JSON Schema: %s
                """.formatted(userMessage, dbContext, jsonSchema);
        } else {
            systemPrompt = """
                You are GymFlow, an elite, hyper-accurate Sports Science AI Coach. You have a zero-tolerance policy for anatomical errors, duplicates, or logical sequencing flaws.
                
                AVAILABLE DATABASE EXERCISES: [%s]
                CURRENT TARGET TRAINING FOCUS: [%s]
                
                CRITICAL OPERATIONAL RULES:
                1. ANATOMICAL ACCURACY: You must maintain perfect muscle target mapping (e.g., Lateral Raises = Medial/Side Delts, Close Grip Bench/Overhead Extensions = Triceps, Bench/Flyes = Chest). Never hallucinate or switch target muscle profiles.
                2. ANTI-DUPLICATION RULE: Review the chat history. Never suggest an exercise that is already active in the user's routine.
                3. WORKOUT STRUCTURE: %s
                4. QUANTITY CONSTRAINT: %s
                5. STICK TO THE DATABASE: Only use exercises provided in the AVAILABLE DATABASE string above. Do not invent new names.
                6. KEEP IT BRIEF: Keep the 'coach_message' short, motivating, and professional.
                
                OUTPUT FORMAT:
                You must output ONLY a valid JSON object matching this schema. No markdown wraps, no trailing text outside the JSON block.
                JSON Schema: %s
                """.formatted(dbContext, currentFocus != null ? currentFocus : "GENERAL", getLogicInstruction(currentFocus), quantityRule, jsonSchema);
        }

        // 6. CALL API (FIXED: History Maintained & Zero Temperature Forced)
        try {
            var response = restClient.post()
                    .header("Authorization", "Bearer " + groqApiKey)
                    .body(Map.of(
                            "model", "llama-3.1-8b-instant",
                            // History is always maintained now, preventing context amnesia during swaps
                            "messages", buildMessages(systemPrompt, history, userMessage),
                            // Temperature 0.0 locks the model out of creative styling, forcing factual schema execution
                            "temperature", 0.0,
                            "response_format", Map.of("type", "json_object")
                    ))
                    .retrieve().body(Map.class);

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> firstChoice = choices.get(0);
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