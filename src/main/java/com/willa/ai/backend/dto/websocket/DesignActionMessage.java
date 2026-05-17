package com.willa.ai.backend.dto.websocket;

import lombok.Data;
import java.util.Map;

@Data
public class DesignActionMessage {
    private String workspaceId;
    private String pageId;
    private String senderEmail;
    
    // Loại hành động: "ADD_LAYER", "MOVE_LAYER", "UPDATE_TEXT", "CHANGE_COLOR", "DELETE_LAYER"
    private String actionType;
    
    private String layerId;
    
    // Chứa thông tin toạ độ, text, url.. 
    private Map<String, Object> payload;
}
