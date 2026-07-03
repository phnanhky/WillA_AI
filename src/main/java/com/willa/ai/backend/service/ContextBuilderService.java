package com.willa.ai.backend.service;

import com.willa.ai.backend.entity.Task;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import com.willa.ai.backend.entity.User;

@Service
public class ContextBuilderService {

    public String buildContext(List<Task> tasks) {
        StringBuilder context = new StringBuilder();
        context.append("Dưới đây là thông tin về các task trong workspace:\n");
        for (Task task : tasks) {
            String assignees = task.getAssignees() != null && !task.getAssignees().isEmpty()
                    ? task.getAssignees().stream().map(User::getFullName).collect(Collectors.joining(", "))
                    : "Chưa có người phụ trách";
            
            context.append(String.format("- Task '%s': Trạng thái %s, Hạn chót %s, Người phụ trách: %s\n", 
                task.getTitle(), task.getStatus(), task.getDueDate(), assignees));
        }
        return context.toString();
    }
}
