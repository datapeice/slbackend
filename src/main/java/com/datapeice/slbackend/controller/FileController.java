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
        private final com.datapeice.slbackend.service.AuditLogService auditLogService;

        public FileController(FileStorageService fileStorageService, UserService userService,
                        com.datapeice.slbackend.service.AuditLogService auditLogService) {
                this.fileStorageService = fileStorageService;
                this.userService = userService;
                this.auditLogService = auditLogService;
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
                        auditLogService.logAction(user.getId(), user.getUsername(), "USER_UPLOAD_AVATAR",
                                        "Загрузил аватар", user.getId(), user.getUsername());
                        return ResponseEntity.ok(Map.of(
                                        "status", "success",
                                        "url", viewUrl,
                                        "message", "Аватар успешно загружен"));
                } catch (IllegalArgumentException e) {
                        auditLogService.logAction(user.getId(), user.getUsername(), "USER_UPLOAD_AVATAR_FAIL",
                                        String.format("Пользователь %s не смог загрузить аватар: %s в %s",
                                                        user.getUsername(),
                                                        e.getMessage(), java.time.LocalDateTime.now()),
                                        null, null);
                        return ResponseEntity.badRequest().body(Map.of(
                                        "status", "error",
                                        "message", e.getMessage()));
                } catch (Exception e) {
                        auditLogService.logAction(user.getId(), user.getUsername(), "USER_UPLOAD_AVATAR_FAIL",
                                        String.format("Пользователь %s не смог загрузить аватар: %s в %s",
                                                        user.getUsername(),
                                                        e.getMessage(), java.time.LocalDateTime.now()),
                                        null, null);
                        return ResponseEntity.internalServerError().body(Map.of(
                                        "status", "error",
                                        "message", "Ошибка при загрузке файла: " + e.getMessage()));
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
                                auditLogService.logAction(user.getId(), user.getUsername(), "USER_DELETE_AVATAR",
                                                "Удалил аватар", user.getId(), user.getUsername());
                                return ResponseEntity.ok(Map.of(
                                                "status", "success",
                                                "message", "Аватар удален"));
                        }
                        auditLogService.logAction(user.getId(), user.getUsername(), "USER_DELETE_AVATAR_FAIL",
                                        "Попытка удаления несуществующего аватара", null, null);
                        return ResponseEntity.badRequest().body(Map.of(
                                        "status", "error",
                                        "message", "У вас нет аватара"));
                } catch (Exception e) {
                        auditLogService.logAction(user.getId(), user.getUsername(), "USER_DELETE_AVATAR_FAIL",
                                        "Ошибка при удалении аватара: " + e.getMessage(), null, null);
                        return ResponseEntity.internalServerError().body(Map.of(
                                        "status", "error",
                                        "message", "Ошибка при удалении аватара"));
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
                        auditLogService.logAction(user.getId(), user.getUsername(), "USER_UPLOAD_APPLICATION_IMAGE",
                                        "Загрузил изображение для заявки", user.getId(), user.getUsername());
                        return ResponseEntity.ok(Map.of(
                                        "status", "success",
                                        "url", fileUrl,
                                        "message", "Изображение загружено"));
                } catch (IllegalArgumentException e) {
                        auditLogService.logAction(user.getId(), user.getUsername(),
                                        "USER_UPLOAD_APPLICATION_IMAGE_FAIL",
                                        String.format("Пользователь %s не смог загрузить изображение для заявки: %s в %s",
                                                        user.getUsername(), e.getMessage(),
                                                        java.time.LocalDateTime.now()),
                                        null, null);
                        return ResponseEntity.badRequest().body(Map.of(
                                        "status", "error",
                                        "message", e.getMessage()));
                } catch (Exception e) {
                        auditLogService.logAction(user.getId(), user.getUsername(),
                                        "USER_UPLOAD_APPLICATION_IMAGE_FAIL",
                                        String.format("Пользователь %s не смог загрузить изображение для заявки: %s в %s",
                                                        user.getUsername(), e.getMessage(),
                                                        java.time.LocalDateTime.now()),
                                        null, null);
                        return ResponseEntity.internalServerError().body(Map.of(
                                        "status", "error",
                                        "message", "Ошибка при загрузке файла"));
                }
        }
}
