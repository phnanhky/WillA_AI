package com.willa.ai.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.willa.ai.backend.dto.response.WorkspaceChatExtractResponse;

public class TestParse {
    public static void main(String[] args) throws Exception {
        String json = "{\"tasks\": [], \"newLists\": [\"Thiết Kế\"]}";
        ObjectMapper mapper = new ObjectMapper();
        WorkspaceChatExtractResponse res = mapper.readValue(json, WorkspaceChatExtractResponse.class);
        System.out.println("Parsed newLists: " + res.getNewLists());
    }
}
