package com.datapeice.slbackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateApplicationRequest {
    @NotBlank(message = "Имя не может быть пустым")
    @Size(max = 50)
    private String firstName;

    @NotBlank(message = "Фамилия не может быть пустой")
    @Size(max = 50)
    private String lastName;

    @NotBlank(message = "Расскажите, почему хотите играть у нас")
    @Size(max = 2000)
    private String whyUs;

    @NotBlank(message = "Укажите, откуда узнали о сервере")
    @Size(max = 1000)
    private String source;

    private boolean makeContent;

    @Size(min = 200, max = 2000, message = "Дополнительная информация должна содержать минимум 200 символов")
    private String additionalInfo;

    @NotNull(message = "Укажите самооценку от 1 до 10")
    private Integer selfRating;

    @NotBlank(message = "reCAPTCHA token is required")
    private String recaptchaToken;
}
