package com.datapeice.slbackend.repository;

import com.datapeice.slbackend.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE " +
            "LOWER(a.actorUsername) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(a.targetUsername) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(a.actionType) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(a.details) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<AuditLog> searchLogs(@Param("query") String query, Pageable pageable);
}
