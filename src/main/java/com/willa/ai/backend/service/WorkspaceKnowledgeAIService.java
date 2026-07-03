package com.willa.ai.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.willa.ai.backend.client.AiServerClient;
import com.willa.ai.backend.dto.request.TaskRequest;
import com.willa.ai.backend.dto.request.WorkspaceKnowledgeAIRequest;
import com.willa.ai.backend.dto.response.WorkspaceKnowledgeAIResponse;
import com.willa.ai.backend.entity.Task;
import com.willa.ai.backend.entity.WorkspaceProject;
import com.willa.ai.backend.entity.enums.TaskStatus;
import com.willa.ai.backend.repository.WorkspaceProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkspaceKnowledgeAIService {

    private final IntentDetectionService intentDetectionService;
    private final WorkspaceQueryHandlerService queryHandlerService;
    private final ContextBuilderService contextBuilderService;
    private final AiServerClient aiServerClient;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;
    private final WorkspaceProjectRepository projectRepository;

    public WorkspaceKnowledgeAIResponse processChat(String email, Long workspaceId, WorkspaceKnowledgeAIRequest request) {
        String question = request.getQuestion();

        // 1. Intent Detection
        String intent = intentDetectionService.detectIntent(question);
        String currentDate = java.time.LocalDate.now().toString();

        if ("CREATE_TASK".equals(intent)) {
            String systemPrompt = "You are an AI task creator. The user wants to create a task. The current date is " + currentDate + ". Extract the task details from the user's message and return ONLY a valid JSON object without any markdown wrapping or extra text. Format:\n{\n  \"title\": \"Task Title\",\n  \"description\": \"Task Description\",\n  \"dueDate\": \"YYYY-MM-DDTHH:mm:00\" (if inferred from message, otherwise omit or use null)\n}";
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", question)
            );
            JsonNode responseNode = aiServerClient.workspaceChat(messages);
            if (responseNode != null && responseNode.has("text")) {
                try {
                    String jsonText = responseNode.get("text").asText().trim();
                    // Remove markdown json block if any
                    if (jsonText.startsWith("```json")) {
                        jsonText = jsonText.substring(7);
                    }
                    if (jsonText.startsWith("```")) {
                        jsonText = jsonText.substring(3);
                    }
                    if (jsonText.endsWith("```")) {
                        jsonText = jsonText.substring(0, jsonText.length() - 3);
                    }
                    jsonText = jsonText.trim();
                    
                    JsonNode taskData = objectMapper.readTree(jsonText);
                    TaskRequest taskRequest = new TaskRequest();
                    taskRequest.setTitle(taskData.has("title") ? taskData.get("title").asText() : "New Task");
                    taskRequest.setDescription(taskData.has("description") ? taskData.get("description").asText() : "");
                    taskRequest.setStatus(TaskStatus.TODO);
                    if (taskData.hasNonNull("dueDate")) {
                        try {
                            taskRequest.setDueDate(LocalDateTime.parse(taskData.get("dueDate").asText()));
                        } catch (Exception e) {
                            // ignore parse error
                        }
                    }

                    // Get first project
                    List<WorkspaceProject> projects = projectRepository.findByWorkspaceIdOrderByPositionAscIdAsc(workspaceId);
                    if (projects.isEmpty()) {
                        throw new RuntimeException("Workspace chưa có Project nào. Vui lòng tạo ít nhất 1 Project trước khi dùng AI tạo task.");
                    }
                    taskRequest.setProjectId(projects.get(0).getId());

                    taskService.createTask(email, workspaceId, taskRequest);
                    
                    return WorkspaceKnowledgeAIResponse.builder()
                            .answer("✅ Đã tạo task thành công: **" + taskRequest.getTitle() + "**")
                            .intent(intent)
                            .modelUsed("grok-build-0.1")
                            .tokenInput(0L)
                            .build();
                } catch (Exception e) {
                    return WorkspaceKnowledgeAIResponse.builder()
                            .answer("❌ Có lỗi xảy ra khi trích xuất thông tin tạo task: " + e.getMessage())
                            .intent(intent)
                            .modelUsed("grok-build-0.1")
                            .tokenInput(0L)
                            .build();
                }
            }
        }

        // 2. Query DB
        List<Task> tasks = queryHandlerService.getWorkspaceTasks(workspaceId);

        // 3. Build Context
        String context = contextBuilderService.buildContext(tasks);

        // 4. Model Routing (Tạm dùng grok-build-0.1 qua Python AI Server)
        String systemPrompt = "You are Workspace Knowledge AI. The current date is " + currentDate + ". Use the provided context to answer user questions accurately. IMPORTANT: You are a READ-ONLY assistant. You CANNOT update or delete tasks. If the user asks you to modify a task, you MUST explicitly decline and state that you can only read information. CRITICAL: When users ask about tasks for a specific person, you MUST strictly match their exact name as requested. Do NOT assume that abbreviated names or similar names (e.g., 'Vuong NM' vs 'Ngo Minh Vuong') are the same person unless explicitly told so. Only return tasks that exactly match the requested person's name. Context: \n" + context;
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", question)
        );

        JsonNode responseNode = aiServerClient.workspaceChat(messages);
        
        String answer = "Tạm thời chưa kết nối được tới mô hình Grok. (Intent: " + intent + ")";
        Long tokens = 0L;
        if (responseNode != null && responseNode.has("text")) {
            answer = responseNode.get("text").asText();
        }

        return WorkspaceKnowledgeAIResponse.builder()
                .answer(answer)
                .intent(intent)
                .modelUsed("grok-build-0.1")
                .tokenInput(tokens)
                .build();
    }
}
