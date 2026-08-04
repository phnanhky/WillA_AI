package com.willa.ai.backend.dto.request;

import com.willa.ai.backend.entity.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name must be at most 255 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 128, message = "Password must be 6–128 characters")
    private String password;

    @NotBlank(message = "Confirm password is required")
    @Size(max = 128, message = "Confirm password must be at most 128 characters")
    private String confirmPassword;

    @Size(max = 40, message = "Phone number must be at most 40 characters")
    private String phoneNumber;

    @Size(max = 255, message = "Occupation must be at most 255 characters")
    private String occupation;

    private java.time.LocalDate dob;

    @NotNull(message = "Gender is required")
    private Gender gender;
}
