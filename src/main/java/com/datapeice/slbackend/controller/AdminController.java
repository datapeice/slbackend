package com.datapeice.slbackend.controller;

import com.datapeice.slbackend.dto.*;
import com.datapeice.slbackend.entity.ApplicationStatus;
import com.datapeice.slbackend.service.ApplicationService;
import com.datapeice.slbackend.service.BadgeService;
import com.datapeice.slbackend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final ApplicationService applicationService;
    private final UserService userService;
    private final BadgeService badgeService;

    public AdminController(ApplicationService applicationService, UserService userService, BadgeService badgeService) {
        this.applicationService = applicationService;
        this.userService = userService;
        this.badgeService = badgeService;
    }

    // Управление заявками
    @GetMapping("/applications")
    public ResponseEntity<List<ApplicationResponse>> getAllApplications(
            @RequestParam(required = false) ApplicationStatus status) {
        if (status != null) {
            return ResponseEntity.ok(applicationService.getApplicationsByStatus(status));
        }
        return ResponseEntity.ok(applicationService.getAllApplications());
    }

    @PatchMapping("/applications/{id}/status")
    public ResponseEntity<?> updateApplicationStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateApplicationStatusRequest request) {
        try {
            ApplicationResponse response = applicationService.updateApplicationStatus(id, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsersForAdmin());
    }

    @PostMapping("/users/{id}/ban")
    public ResponseEntity<?> banUser(
            @PathVariable Long id,
            @Valid @RequestBody BanUserRequest request) {
        try {
            UserResponse response = userService.banUser(id, request.getReason());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/users/{id}/unban")
    public ResponseEntity<?> unbanUser(@PathVariable Long id) {
        try {
            UserResponse response = userService.unbanUser(id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<?> resetUserPassword(@PathVariable Long id) {
        try {
            String newPassword = userService.resetUserPassword(id);
            return ResponseEntity.ok(java.util.Map.of(
                    "status", "success",
                    "message", "Пароль сброшен и отправлен на email пользователя",
                    "temporaryPassword", newPassword
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        try {
            UserResponse response = userService.adminUpdateUser(id, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(java.util.Map.of(
                    "status", "success",
                    "message", "Пользователь удален"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        try {
            UserResponse response = userService.createUser(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ==================== Badge Management ====================

    @GetMapping("/badges")
    public ResponseEntity<List<BadgeResponse>> getAllBadges() {
        return ResponseEntity.ok(badgeService.getAllBadges());
    }

    @PostMapping("/badges")
    public ResponseEntity<?> createBadge(@Valid @RequestBody BadgeRequest request) {
        try {
            BadgeResponse response = badgeService.createBadge(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PatchMapping("/badges/{id}")
    public ResponseEntity<?> updateBadge(@PathVariable Long id, @RequestBody BadgeRequest request) {
        try {
            BadgeResponse response = badgeService.updateBadge(id, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/badges/{id}")
    public ResponseEntity<?> deleteBadge(@PathVariable Long id) {
        try {
            badgeService.deleteBadge(id);
            return ResponseEntity.ok(java.util.Map.of("status", "success", "message", "Badge deleted"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/users/{userId}/badges/{badgeId}")
    public ResponseEntity<?> assignBadgeToUser(@PathVariable Long userId, @PathVariable Long badgeId) {
        try {
            badgeService.assignBadgeToUser(userId, badgeId);
            return ResponseEntity.ok(java.util.Map.of("status", "success", "message", "Badge assigned"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/users/{userId}/badges/{badgeId}")
    public ResponseEntity<?> removeBadgeFromUser(@PathVariable Long userId, @PathVariable Long badgeId) {
        try {
            badgeService.removeBadgeFromUser(userId, badgeId);
            return ResponseEntity.ok(java.util.Map.of("status", "success", "message", "Badge removed"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
