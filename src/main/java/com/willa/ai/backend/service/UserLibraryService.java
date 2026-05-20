package com.willa.ai.backend.service;

import com.willa.ai.backend.dto.request.AddUserLibraryImageRequest;
import com.willa.ai.backend.dto.response.UserLibraryImageResponse;

import java.util.List;

public interface UserLibraryService {
    List<UserLibraryImageResponse> getUserLibraryImages(String email);
    List<UserLibraryImageResponse> addUserLibraryImage(String email, AddUserLibraryImageRequest request);
}
