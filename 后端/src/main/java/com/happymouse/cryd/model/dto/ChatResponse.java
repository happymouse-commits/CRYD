package com.happymouse.cryd.model.dto;

import java.util.List;

/**
 * 聊天响应
 */
public class ChatResponse {
    private String message;
    private String agentName;
    private String agentDescription;
    private boolean profileUpdated;
    private boolean resourceGenerated;
    private List<Long> resourceIds;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getAgentDescription() { return agentDescription; }
    public void setAgentDescription(String agentDescription) { this.agentDescription = agentDescription; }
    public boolean isProfileUpdated() { return profileUpdated; }
    public void setProfileUpdated(boolean profileUpdated) { this.profileUpdated = profileUpdated; }
    public boolean isResourceGenerated() { return resourceGenerated; }
    public void setResourceGenerated(boolean resourceGenerated) { this.resourceGenerated = resourceGenerated; }
    public List<Long> getResourceIds() { return resourceIds; }
    public void setResourceIds(List<Long> resourceIds) { this.resourceIds = resourceIds; }
}
