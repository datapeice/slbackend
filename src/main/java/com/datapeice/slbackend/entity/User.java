package com.datapeice.slbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true)
    private String discordNickname;

    @Column(unique = true)
    private String minecraftNickname;

    @Enumerated(EnumType.STRING)
    private UserRole role = UserRole.ROLE_USER;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    private boolean banned = false;

    private boolean inSeason = false;

    private String banReason;

    // Email verification
    private boolean emailVerified = false;

    private String emailVerificationToken;

    private Long emailVerificationTokenExpiry;

    // TOTP (2FA)
    private boolean totpEnabled = false;

    private String totpSecret;

    // Password Reset
    private String resetPasswordToken;

    private Long resetPasswordTokenExpiry;

    // Bio
    @Column(columnDefinition = "TEXT")
    private String bio;

    // Is accepted player
    private boolean isPlayer = false;

    // Discord User ID for DM notifications and role management
    private String discordUserId;

    // Discord OAuth verification - account is active only when discord is verified
    private boolean discordVerified = false;

    // Discord OAuth state to prevent JWT leakage
    private String discordOauthState;

    // Is the user currently a member of the linked Discord server
    @Column(columnDefinition = "boolean default false")
    private boolean inDiscord = false;

    // Security logging
    private String registrationIp;
    private String registrationUserAgent;

    private String lastLoginIp1;
    private String lastLoginUserAgent1;
    private String lastLoginIp2;
    private String lastLoginUserAgent2;

    // JWT Token Version for instant invalidation on security events
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer tokenVersion = 0;

    // Badges
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_badges", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "badge_id"))
    private Set<Badge> badges = new HashSet<>();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
