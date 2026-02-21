package com.datapeice.slbackend.dto;

import com.datapeice.slbackend.entity.ApplicationStatus;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ApplicationResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String whyUs;
    private String source;
    private boolean makeContent;
    private String additionalInfo;
    private Integer selfRating;
    private ApplicationStatus status;
    private String adminComment;
    private LocalDateTime createdAt;
    private UserSummary user;

    @Data
    public static class UserSummary {
        private Long id;
        private String username;
        private String email;
        private String discordNickname;
        private String minecraftNickname;
        private String avatarUrl;
    }
}

