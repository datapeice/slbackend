package com.datapeice.slbackend.repository;

import com.datapeice.slbackend.entity.Application;
import com.datapeice.slbackend.entity.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findAllByUserId(Long userId);

    List<Application> findAllByStatus(ApplicationStatus status);
    List<Application> findAllByUserIdAndStatus(Long userId, ApplicationStatus status);

}
