package com.project.aiagent.controller;

import com.project.aiagent.dto.AgentRequest;
import com.project.aiagent.service.GymAgentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agent")

@CrossOrigin(origins = {"https://gym-flow-ashy-three.vercel.app"}, allowCredentials = "true")
public class AgentController {

    private final GymAgentService agentService;


    @Value("${internal.api.key:gymflow-secret-connect-2026}")
    private String internalKey;

    public AgentController(GymAgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public ResponseEntity<String> chat(@RequestBody AgentRequest request) {

        // 1. Security Check using the injected key
        if (!internalKey.equals(request.getApiKey())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"error\": \"ACCESS DENIED\"}");
        }

        // 2. Call the AI Brain
        String response = agentService.getCoaching(request.getUserId(), request.getMessage());
        return ResponseEntity.ok(response);
    }
}