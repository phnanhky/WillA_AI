package com.willa.ai.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkspaceChatExtractResponse {
    @com.fasterxml.jackson.annotation.JsonProperty("tasks")
    private List<ExtractedTask> tasks;
    
    @com.fasterxml.jackson.annotation.JsonProperty("newLists")
    private List<String> newLists;

    public List<String> getNewLists() { return newLists; }
    public void setNewLists(List<String> newLists) { this.newLists = newLists; }
    public List<ExtractedTask> getTasks() { return tasks; }
    public void setTasks(List<ExtractedTask> tasks) { this.tasks = tasks; }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedTask {
        private String assignee;
        private Long assigneeUserId; // matched member id if available
        private String task;
        private String deadline;
        private String priority;
        private String status;
        private List<ExtractedChecklistItem> checklists;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedChecklistItem {
        private String title;
        private Long assigneeUserId;
        private String deadline;
        private String priority;
    }
}
