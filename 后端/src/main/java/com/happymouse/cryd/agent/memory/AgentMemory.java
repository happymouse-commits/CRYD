package com.happymouse.cryd.agent.memory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent记忆管理 — 每个Agent独立的对话上下文窗口
 * 生产环境可替换为Redis实现
 */
public class AgentMemory {

    private final String agentName;
    private final int maxHistorySize;
    private final Map<Long, List<Map<String, String>>> conversations = new ConcurrentHashMap<>();

    public AgentMemory(String agentName, int maxHistorySize) {
        this.agentName = agentName;
        this.maxHistorySize = maxHistorySize;
    }

    public AgentMemory(String agentName) {
        this(agentName, 20);
    }

    public String getAgentName() { return agentName; }

    public void save(Long studentId, String role, String content) {
        List<Map<String, String>> history = conversations.computeIfAbsent(studentId, k -> new ArrayList<>());
        history.add(Map.of("role", role, "content", content));
        if (history.size() > maxHistorySize) {
            history.remove(0);
        }
    }

    public List<Map<String, String>> load(Long studentId) {
        return conversations.getOrDefault(studentId, Collections.emptyList());
    }

    public void clear(Long studentId) {
        conversations.remove(studentId);
    }

    public int getHistorySize(Long studentId) {
        List<Map<String, String>> history = conversations.get(studentId);
        return history != null ? history.size() : 0;
    }
}
