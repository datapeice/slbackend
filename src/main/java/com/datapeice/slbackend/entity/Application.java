package com.datapeice.slbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "applications")
@Data
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String firstName;
    private String lastName;

    @Column(columnDefinition = "TEXT")
    private String whyUs;

    @Column(columnDefinition = "TEXT")
    private String source;

    private boolean makeContent;

    @Column(columnDefinition = "TEXT")
    private String additionalInfo;

    private Integer selfRating;

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    private String adminComment;

    @CreationTimestamp
    private LocalDateTime createdAt;

}
