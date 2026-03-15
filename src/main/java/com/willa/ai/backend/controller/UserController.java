package com.willa.ai.backend.controller;

import com.willa.ai.backend.dto.response.ApiResponse;
import com.willa.ai.backend.dto.response.UserResponse;
import com.willa.ai.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Users", description = "User management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/all")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Users fetched successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
            Page<UserResponse> users = userService.getAllUsers(pageable);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("Users fetched successfully")
                    .data(users)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @GetMapping("/{userId}")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User fetched successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> getUserById(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long userId) {
        try {
            UserResponse user = userService.getUserById(userId);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("User fetched successfully")
                    .data(user)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @GetMapping("/me")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User info fetched successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> getMyInfo(Authentication authentication) {
        try {
            String email = authentication.getName();
            UserResponse user = userService.getMyInfo(email);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("User info fetched successfully")
                    .data(user)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PutMapping("/{userId}")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User updated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> updateUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long userId,
            @Parameter(description = "Full name (optional)")
            @RequestParam(required = false) String fullName,
            @Parameter(description = "Phone number (optional)")
            @RequestParam(required = false) String phoneNumber) {
        try {
            UserResponse user = userService.updateUser(userId, fullName, phoneNumber);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("User updated successfully")
                    .data(user)
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/{userId}/active")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User activated successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<?> activeUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable Long userId) {
        try {
            userService.activeUser(userId);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("User activated successfully")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<?> deactivateUser(@PathVariable Long userId) {
        try {
            userService.deactivateUser(userId);
            return ResponseEntity.ok(ApiResponse.builder()
                    .status(true)
                    .message("User deactivated successfully")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.builder()
                            .status(false)
                            .message(e.getMessage())
                            .build());
        }
    }
}
