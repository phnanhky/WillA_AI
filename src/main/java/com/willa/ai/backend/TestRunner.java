package com.willa.ai.backend;

import com.willa.ai.backend.dto.request.WorkspaceKnowledgeAIRequest;
import com.willa.ai.backend.service.WorkspaceKnowledgeAIService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TestRunner implements CommandLineRunner {

    private final WorkspaceKnowledgeAIService service;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("--- BẮT ĐẦU TEST KNOWLEDGE AI ---");
        try {
            WorkspaceKnowledgeAIRequest request = new WorkspaceKnowledgeAIRequest();
            request.setQuestion("Có task nào đang In Progress không?");
            // Sử dụng một workspace ID giả định (ví dụ ID = 1)
            var response = service.processChat("test@example.com", 1L, request);
            System.out.println("Intent: " + response.getIntent());
            System.out.println("Answer: " + response.getAnswer());
        } catch (Exception e) {
            System.out.println("Lỗi test: " + e.getMessage());
        }
        System.out.println("--- KẾT THÚC TEST KNOWLEDGE AI ---");
    }
}
