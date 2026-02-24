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

}
