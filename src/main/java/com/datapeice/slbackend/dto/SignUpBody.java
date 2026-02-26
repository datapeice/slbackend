package com.datapeice.slbackend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignUpBody {
    @NotBlank(message = "Username cannot be empty")
    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    private String username;

    @NotBlank(message = "Password cannot be empty")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Pattern(regexp = "^(?=.*[0-9])(?=.*[\\p{Ll}])(?=.*[\\p{Lu}])(?=.*[\\p{P}\\p{S}]).*$", message = "Password must contain at least one digit, one lowercase, one uppercase letter and one special character")
    private String password;

    @Email(message = "Некорректный формат email")
    @NotBlank
    private String email;

    private String discordNickname;

    @NotBlank(message = "Укажите ваш никнейм в Minecraft")
    private String minecraftNickname;

    @NotBlank(message = "reCAPTCHA token is required")
    private String recaptchaToken;
}
