package com.project.aiagent.dto;

import lombok.Data;

@Data
public class AgentRequest {
    private String userId;     // Who is asking?
    private String message;    // What are they saying? ("I want a chest workout")
    private String apiKey;     // Simple password so only YOUR app can use it
}