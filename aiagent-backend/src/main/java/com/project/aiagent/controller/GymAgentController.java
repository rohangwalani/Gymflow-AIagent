package com.project.aiagent.controller;

import com.project.aiagent.service.GymAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coach")
// @CrossOrigin...  <-- REMOVED (Handled globally by WebConfig)
public class GymAgentController {

    private final GymAgentService agentService;

    public GymAgentController(GymAgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("GymFlow Backend is Active");
    }

    @GetMapping("/ask")
    public ResponseEntity<String> ask(
            @RequestParam String message,
            @RequestParam(defaultValue = "guest") String userId
    ) {
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Please enter a valid question.");
        }

        if (message.length() > 1000) {
            return ResponseEntity.badRequest().body("Your message is too long.");
        }

        // Logic is delegated to the Service
        String response = agentService.getCoaching(userId, message.trim());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("OK");
    }
}