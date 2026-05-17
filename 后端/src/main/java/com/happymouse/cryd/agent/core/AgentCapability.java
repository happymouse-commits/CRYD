package com.happymouse.cryd.agent.core;

/**
 * Agent能力描述 — 供调度器进行能力匹配
 */
public class AgentCapability {

    private String id;
    private String description;
    private double confidence;

    public AgentCapability() {}

    public AgentCapability(String id, String description, double confidence) {
        this.id = id;
        this.description = description;
        this.confidence = confidence;
    }

    public static AgentCapability of(String id, String description, double confidence) {
        return new AgentCapability(id, description, confidence);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    /**
     * 计算此能力与需求描述的简单匹配度（基于关键词Jaccard相似度）
     */
    public double matchScore(String requirement) {
        if (requirement == null || requirement.isEmpty()) return 0;
        String[] reqWords = requirement.toLowerCase().split("[\\s,，、]+");
        String[] capWords = description.toLowerCase().split("[\\s,，、]+");

        int match = 0;
        for (String rw : reqWords) {
            for (String cw : capWords) {
                if (rw.contains(cw) || cw.contains(rw)) {
                    match++;
                    break;
                }
            }
        }
        double jaccard = (double) match / (reqWords.length + capWords.length - match);
        return jaccard * confidence;
    }

    @Override
    public String toString() {
        return "AgentCapability{id='" + id + "', desc='" + description + "', confidence=" + confidence + "}";
    }
}
