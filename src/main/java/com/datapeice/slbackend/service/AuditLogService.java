package com.datapeice.slbackend.service;

import com.datapeice.slbackend.entity.AuditLog;
import com.datapeice.slbackend.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    public void logAction(Long actorId, String actorUsername, String actionType, String details, Long targetUserId,
            String targetUsername) {
        AuditLog log = new AuditLog();
        log.setActorId(actorId);
        log.setActorUsername(actorUsername);
        log.setActionType(actionType);
        log.setDetails(details);
        log.setTargetUserId(targetUserId);
        log.setTargetUsername(targetUsername);
        log.setCreatedAt(LocalDateTime.now());

        auditLogRepository.save(log);
    }

    public Page<AuditLog> getLogs(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return auditLogRepository.findAll(pageable);
        } else {
            return auditLogRepository.searchLogs(query.trim(), pageable);
        }
    }
}
