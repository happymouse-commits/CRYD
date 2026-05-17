package com.happymouse.cryd.model.dto;

/**
 * 聊天请求
 */
public class ChatRequest {
    private Long studentId;
    private String message;
    private String agentName;
    private String mode; // fast / expert

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
