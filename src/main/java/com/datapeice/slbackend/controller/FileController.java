package com.datapeice.slbackend.controller;

import com.datapeice.slbackend.entity.User;
import com.datapeice.slbackend.service.FileStorageService;
import com.datapeice.slbackend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageService fileStorageService;
    private final UserService userService;

    public FileController(FileStorageService fileStorageService, UserService userService) {
        this.fileStorageService = fileStorageService;
        this.userService = userService;
    }

    @PostMapping("/upload/avatar")
    public ResponseEntity<?> uploadAvatar(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) {
        try {
            if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                fileStorageService.deleteFile(user.getAvatarUrl());
            }
            String objectKey = fileStorageService.uploadFile(file, "avatars");

            user.setAvatarUrl(objectKey);
            userService.updateUserProfile(user, null);

            // Resolve to a viewable URL for the response
            String viewUrl = fileStorageService.resolveUrl(objectKey);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "url", viewUrl,
                    "message", "Аватар успешно загружен"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Ошибка при загрузке файла: " + e.getMessage()
            ));
        }
    }

    /**
     * Удаление аватара пользователя
     */
    @DeleteMapping("/avatar")
    public ResponseEntity<?> deleteAvatar(@AuthenticationPrincipal User user) {
        try {
            if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                fileStorageService.deleteFile(user.getAvatarUrl());
                user.setAvatarUrl(null);
                userService.updateUserProfile(user, null);

                return ResponseEntity.ok(Map.of(
                        "status", "success",
                        "message", "Аватар удален"
                ));
            }
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "У вас нет аватара"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Ошибка при удалении аватара"
            ));
        }
    }

    /**
     * Загрузка изображения для заявки (опционально)
     */
    @PostMapping("/upload/application-image")
    public ResponseEntity<?> uploadApplicationImage(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file) {
        try {
            String fileUrl = fileStorageService.uploadFile(file, "applications");

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "url", fileUrl,
                    "message", "Изображение загружено"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Ошибка при загрузке файла"
            ));
        }
    }
}

