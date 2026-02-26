package com.datapeice.slbackend.repository;

import com.datapeice.slbackend.entity.Application;
import com.datapeice.slbackend.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findAllByUserId(Long userId);

    Page<Application> findAllByStatus(ApplicationStatus status, Pageable pageable);

    List<Application> findAllByUserIdAndStatus(Long userId, ApplicationStatus status);

    void deleteAllByUserId(Long userId);

}
