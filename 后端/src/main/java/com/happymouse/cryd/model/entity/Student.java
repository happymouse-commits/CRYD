package com.happymouse.cryd.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 学生实体
 */
@Entity
@Table(name = "student")
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(length = 50)
    private String nickname;

    private Integer knowledgeLevel;

    @Column(length = 20)
    private String cognitiveStyle;

    @Column(length = 20)
    private String learningPreference;

    @Column(length = 20)
    private String learningPace;

    private Integer progress;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (progress == null) progress = 0;
        if (knowledgeLevel == null) knowledgeLevel = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Integer getKnowledgeLevel() { return knowledgeLevel; }
    public void setKnowledgeLevel(Integer knowledgeLevel) { this.knowledgeLevel = knowledgeLevel; }
    public String getCognitiveStyle() { return cognitiveStyle; }
    public void setCognitiveStyle(String cognitiveStyle) { this.cognitiveStyle = cognitiveStyle; }
    public String getLearningPreference() { return learningPreference; }
    public void setLearningPreference(String learningPreference) { this.learningPreference = learningPreference; }
    public String getLearningPace() { return learningPace; }
    public void setLearningPace(String learningPace) { this.learningPace = learningPace; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
