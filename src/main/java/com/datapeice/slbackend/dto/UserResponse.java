package com.datapeice.slbackend.dto;

import com.datapeice.slbackend.entity.UserRole;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String discordNickname;
    private String minecraftNickname;
    private UserRole role;
    private String avatarUrl;
    private boolean banned;
    private String banReason;
    private boolean emailVerified;
    private boolean totpEnabled;
    private String bio;
    @JsonProperty("isPlayer")
    private boolean isPlayer;
    private String discordUserId;
    private boolean discordVerified;

    // Badges (without @SL role)
    private List<BadgeResponse> badges;

    // Security info (admin-only fields, populated only for admin endpoint)
    private String registrationIp;
    private String registrationUserAgent;
    private String lastLoginIp1;
    private String lastLoginUserAgent1;
    private String lastLoginIp2;
    private String lastLoginUserAgent2;
}


