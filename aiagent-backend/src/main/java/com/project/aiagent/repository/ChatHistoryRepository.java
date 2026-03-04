package com.project.aiagent.repository;

import com.project.aiagent.model.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {
    // This fetches the last 10 messages so the AI knows the current conversation context
    List<ChatHistory> findTop10ByUserEmailOrderByTimestampDesc(String userEmail);
}