package com.willa.ai.backend.service.impl;

import com.willa.ai.backend.dto.request.AddUserLibraryImageRequest;
import com.willa.ai.backend.dto.response.UserLibraryImageResponse;
import com.willa.ai.backend.entity.User;
import com.willa.ai.backend.entity.UserLibraryImage;
import com.willa.ai.backend.repository.UserLibraryImageRepository;
import com.willa.ai.backend.repository.UserRepository;
import com.willa.ai.backend.service.UserLibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserLibraryServiceImpl implements UserLibraryService {
    private final UserRepository userRepository;
    private final UserLibraryImageRepository userLibraryImageRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UserLibraryImageResponse> getUserLibraryImages(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapList(user.getId());
    }

    @Override
    @Transactional
    public List<UserLibraryImageResponse> addUserLibraryImage(String email, AddUserLibraryImageRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        String imageUrl = request.getImageUrl().trim();

        if (userLibraryImageRepository.findByUserIdAndImageUrl(user.getId(), imageUrl).isEmpty()) {
            userLibraryImageRepository.save(UserLibraryImage.builder()
                    .user(user)
                    .imageUrl(imageUrl)
                    .fileSizeBytes(request.getFileSizeBytes())
                    .build());
        }
        return mapList(user.getId());
    }

    private List<UserLibraryImageResponse> mapList(Long userId) {
        return userLibraryImageRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(img -> UserLibraryImageResponse.builder()
                        .id(img.getId())
                        .imageUrl(img.getImageUrl())
                        .fileSizeBytes(img.getFileSizeBytes())
                        .createdAt(img.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}
