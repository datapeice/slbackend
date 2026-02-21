package com.datapeice.slbackend.controller;

import com.datapeice.slbackend.dto.ApplicationResponse;
import com.datapeice.slbackend.dto.CreateApplicationRequest;
import com.datapeice.slbackend.entity.User;
import com.datapeice.slbackend.service.ApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {
    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<?> createApplication(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateApplicationRequest request) {
        try {
            ApplicationResponse response = applicationService.createApplication(user, request);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/my")
    public ResponseEntity<List<ApplicationResponse>> getMyApplication(@AuthenticationPrincipal User user) {
        try {
            List<ApplicationResponse> response = applicationService.getMyApplication(user);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            // This case shouldn't happen with current service logic but keeping structure
            return ResponseEntity.badRequest().build();
        }
    }
}
