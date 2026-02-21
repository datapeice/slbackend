package com.datapeice.slbackend.service;

import com.datapeice.slbackend.dto.AdminCreateUserRequest;
import com.datapeice.slbackend.dto.AdminUpdateUserRequest;
import com.datapeice.slbackend.dto.BadgeResponse;
import com.datapeice.slbackend.dto.UpdateUserRequest;
import com.datapeice.slbackend.dto.UserResponse;
import com.datapeice.slbackend.entity.User;
import com.datapeice.slbackend.entity.UserRole;
import com.datapeice.slbackend.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final DiscordService discordService;
    private final GeoIpService geoIpService;
    private final FileStorageService fileStorageService;

    public UserService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder,
                       EmailService emailService, DiscordService discordService, GeoIpService geoIpService,
                       FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.discordService = discordService;
        this.geoIpService = geoIpService;
        this.fileStorageService = fileStorageService;
    }

    @Transactional(readOnly = true)
    public UserResponse getUserProfile(User user) {
        // Re-fetch from DB to ensure session is open for EAGER collections
        User fresh = userRepository.findById(user.getId()).orElse(user);
        // Auto-sync Discord avatar if missing
        if ((fresh.getAvatarUrl() == null || fresh.getAvatarUrl().isBlank()) && fresh.getDiscordUserId() != null) {
            syncDiscordAvatarForUser(fresh);
        }
        return mapToResponse(fresh);
    }

    @Transactional
    public void syncDiscordAvatarForUser(User user) {
        if (user.getDiscordUserId() == null) return;
        String url = discordService.syncDiscordAvatar(user.getDiscordUserId());
        if (url != null) {
            user.setAvatarUrl(url);
            userRepository.save(user);
        }
    }

    @Transactional
    public UserResponse updateUserProfile(User user, UpdateUserRequest request) {
        // Если request null - обновляем только то что уже изменено в user (например аватар)
        if (request == null) {
            User updated = userRepository.save(user);
            return mapToResponse(updated);
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email уже используется");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getDiscordNickname() != null && !request.getDiscordNickname().equals(user.getDiscordNickname())) {
            if (userRepository.existsByDiscordNickname(request.getDiscordNickname())) {
                throw new IllegalArgumentException("Discord никнейм уже используется");
            }
            user.setDiscordNickname(request.getDiscordNickname());
            // Re-resolve Discord user ID and pull fresh avatar for new nickname
            if (discordService.isEnabled()) {
                discordService.findDiscordUserId(request.getDiscordNickname())
                        .ifPresent(id -> {
                            user.setDiscordUserId(id);
                            String newAvatar = discordService.syncDiscordAvatar(id);
                            if (newAvatar != null) {
                                user.setAvatarUrl(newAvatar);
                            }
                        });
            }
        }

        if (request.getMinecraftNickname() != null && !request.getMinecraftNickname().equals(user.getMinecraftNickname())) {
            if (userRepository.existsByMinecraftNickname(request.getMinecraftNickname())) {
                throw new IllegalArgumentException("Minecraft никнейм уже используется");
            }
            user.setMinecraftNickname(request.getMinecraftNickname());
        }

        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
            if (request.getOldPassword() == null || request.getOldPassword().isBlank()) {
                throw new IllegalArgumentException("Для смены пароля необходимо ввести старый пароль");
            }
            if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Неверный старый пароль");
            }
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        }

        User updated = userRepository.save(user);
        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        // Возвращаем только пользователей с подтвержденным email И принятой заявкой (isPlayer = true)
        return userRepository.findAll().stream()
                .filter(User::isPlayer)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsersForAdmin() {
        // Для админа - все пользователи, с security info
        return userRepository.findAll().stream()
                .map(u -> mapToResponse(u, true))
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse banUser(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        user.setBanned(true);
        user.setBanReason(reason);
        user.setPlayer(false);

        // Resolve Discord user ID if needed
        if (user.getDiscordUserId() == null && discordService.isEnabled()) {
            discordService.findDiscordUserId(user.getDiscordNickname())
                    .ifPresent(user::setDiscordUserId);
        }

        User updated = userRepository.save(user);

        if (user.getDiscordUserId() != null && discordService.isEnabled()) {
            discordService.removeSlRole(user.getDiscordUserId());
            discordService.sendDirectMessage(user.getDiscordUserId(),
                    "🚫 **StoryLegends** — Ваш аккаунт был **заблокирован** администрацией.\n" +
                    "**Причина:** " + (reason != null ? reason : "Причина не указана") + "\n" +
                    "***С уважением, <:slteam:1244336090928906351>***");
        }

        return mapToResponse(updated);
    }

    @Transactional
    public UserResponse unbanUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        user.setBanned(false);
        user.setBanReason(null);
        user.setPlayer(true);

        // Resolve Discord user ID if needed
        if (user.getDiscordUserId() == null && discordService.isEnabled()) {
            discordService.findDiscordUserId(user.getDiscordNickname())
                    .ifPresent(user::setDiscordUserId);
        }

        User updated = userRepository.save(user);

        if (user.getDiscordUserId() != null && discordService.isEnabled()) {
            discordService.assignSlRole(user.getDiscordUserId());
            discordService.sendDirectMessage(user.getDiscordUserId(),
                    "✅ **StoryLegends** — Ваш аккаунт был **разблокирован** администрацией.\n" +
                    "Добро пожаловать обратно!\n" +
                    "***С уважением, <:slteam:1244336090928906351>***");
        }

        return mapToResponse(updated);
    }

    // Admin methods

    @Transactional
    public String resetUserPassword(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        String newPassword = generateRandomPassword();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Отправляем email с новым паролем
        emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), newPassword);

        return newPassword;
    }

    @Transactional
    public UserResponse adminUpdateUser(Long userId, AdminUpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new IllegalArgumentException("Имя пользователя уже занято");
            }
            user.setUsername(request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email уже используется");
            }
            user.setEmail(request.getEmail());
            // Reset email verification — user must confirm new email
            user.setEmailVerified(false);
            String verificationToken = java.util.UUID.randomUUID().toString();
            user.setEmailVerificationToken(verificationToken);
            user.setEmailVerificationTokenExpiry(System.currentTimeMillis() + 86400000L); // 24h
            emailService.sendVerificationEmail(request.getEmail(), user.getUsername(), verificationToken);
        }

        if (request.getDiscordNickname() != null && !request.getDiscordNickname().equals(user.getDiscordNickname())) {
            if (userRepository.existsByDiscordNickname(request.getDiscordNickname())) {
                throw new IllegalArgumentException("Discord никнейм уже используется");
            }
            user.setDiscordNickname(request.getDiscordNickname());
        }

        if (request.getMinecraftNickname() != null && !request.getMinecraftNickname().equals(user.getMinecraftNickname())) {
            if (userRepository.existsByMinecraftNickname(request.getMinecraftNickname())) {
                throw new IllegalArgumentException("Minecraft никнейм уже используется");
            }
            user.setMinecraftNickname(request.getMinecraftNickname());
        }

        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        if (request.getIsPlayer() != null) {
            boolean wasPlayer = user.isPlayer();
            boolean nowPlayer = request.getIsPlayer();
            user.setPlayer(nowPlayer);

            // Sync @SL Discord role
            if (wasPlayer != nowPlayer && discordService.isEnabled()) {
                // Resolve Discord user ID if not yet saved
                if (user.getDiscordUserId() == null) {
                    discordService.findDiscordUserId(user.getDiscordNickname())
                            .ifPresent(id -> {
                                user.setDiscordUserId(id);
                            });
                }
                if (user.getDiscordUserId() != null) {
                    if (nowPlayer) {
                        discordService.assignSlRole(user.getDiscordUserId());
                        discordService.sendDirectMessage(user.getDiscordUserId(),
                                "**Приветствую!**\n" +
                                        "Вам выдана роль @SL на сервере StoryLegends\n" +
                                        "Добро пожаловать на наш сервер, дабы **начать играть** вам нужно **прочитать** канал <#1229044440178626660>.\n" +
                                        "Так-же если вы ещё не ознакомилсь с [правилами](https://www.storylegends.xyz/rules) сервера, то обязательно это сделайте!\n" +
                                        "**Удачной игры**\n" +
                                        "***С уважением, <:slteam:1244336090928906351>***");
                    } else {
                        discordService.removeSlRole(user.getDiscordUserId());
                        discordService.sendDirectMessage(user.getDiscordUserId(),
                                "**StoryLegends** — Ваш статус игрока был отозван администрацией. Роль @SL удалена.\n" +
                                        "**С уважением, <:slteam:1244336090928906351>**");
                    }
                }
            }
        }

        User updated = userRepository.save(user);
        return mapToResponse(updated, true);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        userRepository.delete(user);
    }

    @Transactional
    public UserResponse createUser(AdminCreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Имя пользователя уже занято");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email уже используется");
        }

        if (userRepository.existsByDiscordNickname(request.getDiscordNickname())) {
            throw new IllegalArgumentException("Discord никнейм уже используется");
        }

        if (userRepository.existsByMinecraftNickname(request.getMinecraftNickname())) {
            throw new IllegalArgumentException("Minecraft никнейм уже используется");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setDiscordNickname(request.getDiscordNickname());
        user.setMinecraftNickname(request.getMinecraftNickname());
        user.setBio(request.getBio());
        user.setRole(UserRole.ROLE_USER);
        user.setEmailVerified(request.isEmailVerified());

        User saved = userRepository.save(user);
        return mapToResponse(saved);
    }

    @Transactional
    public void processForgotPassword(String email) {
        // Find user by email
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = java.util.UUID.randomUUID().toString();
            user.setResetPasswordToken(token);
            // 1 hour expiry
            user.setResetPasswordTokenExpiry(System.currentTimeMillis() + 3600000);
            userRepository.save(user);

            emailService.sendForgotPasswordEmail(user.getEmail(), user.getUsername(), token);
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetPasswordToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Неверный или истекший токен восстановления"));

        if (user.getResetPasswordTokenExpiry() == null || user.getResetPasswordTokenExpiry() < System.currentTimeMillis()) {
            throw new IllegalArgumentException("Срок действия токена истек");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        userRepository.save(user);
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%^&+=";
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < 12; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }

        return password.toString();
    }

    private UserResponse mapToResponse(User user) {
        return mapToResponse(user, false);
    }

    /**
     * Resolves an avatar URL or object key to a viewable URL.
     * - Plain object key (e.g., "avatars/uuid.png") → generates presigned/public URL
     * - Old full S3/MinIO URL → extracts object key, then generates fresh URL
     * - External URL (Discord CDN etc.) → returned as-is
     */
    private String resolveAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) return null;
        if (!avatarUrl.startsWith("http://") && !avatarUrl.startsWith("https://")) {
            // Stored as object key — generate fresh URL
            try {
                return fileStorageService.resolveUrl(avatarUrl);
            } catch (Exception e) {
                return avatarUrl;
            }
        }
        // It's a full URL — try to extract object key and re-resolve (handles expired presigned URLs)
        try {
            String objectKey = fileStorageService.extractObjectKey(avatarUrl);
            if (objectKey != null) {
                return fileStorageService.resolveUrl(objectKey);
            }
        } catch (Exception ignored) {
        }
        // Fall back to original URL (e.g., Discord CDN)
        return avatarUrl;
    }

    private UserResponse mapToResponse(User user, boolean includeSecurityInfo) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setDiscordNickname(user.getDiscordNickname());
        response.setMinecraftNickname(user.getMinecraftNickname());
        response.setRole(user.getRole());
        response.setAvatarUrl(resolveAvatarUrl(user.getAvatarUrl()));
        response.setBanned(user.isBanned());
        response.setBanReason(user.getBanReason());
        response.setEmailVerified(user.isEmailVerified());
        response.setTotpEnabled(user.isTotpEnabled());
        response.setBio(user.getBio());
        response.setPlayer(user.isPlayer());
        response.setDiscordUserId(user.getDiscordUserId());

        // Badges (no @SL role - that's internal Discord only)
        if (user.getBadges() != null) {
            List<BadgeResponse> badges = user.getBadges().stream()
                    .map(b -> {
                        BadgeResponse br = new BadgeResponse();
                        br.setId(b.getId());
                        br.setName(b.getName());
                        br.setColor(b.getColor());
                        br.setSvgIcon(b.getSvgIcon());
                        // discordRoleId not exposed to public
                        br.setCreatedAt(b.getCreatedAt());
                        return br;
                    })
                    .collect(Collectors.toList());
            response.setBadges(badges);
        }

        // Security info - only for admin view
        if (includeSecurityInfo) {
            response.setRegistrationIp(user.getRegistrationIp());
            response.setRegistrationUserAgent(user.getRegistrationUserAgent());
            response.setLastLoginIp1(user.getLastLoginIp1());
            response.setLastLoginUserAgent1(user.getLastLoginUserAgent1());
            response.setLastLoginIp2(user.getLastLoginIp2());
            response.setLastLoginUserAgent2(user.getLastLoginUserAgent2());
        }

        return response;
    }

    @Transactional
    public void recordLogin(String username, String ip, String userAgent) {
        userRepository.findByUsername(username).ifPresent(user -> {
            String geoIp = geoIpService.formatIpWithGeo(ip);

            // Shift: 1->2, new->1
            user.setLastLoginIp2(user.getLastLoginIp1());
            user.setLastLoginUserAgent2(user.getLastLoginUserAgent1());
            user.setLastLoginIp1(geoIp);
            user.setLastLoginUserAgent1(userAgent);

            // Resolve Discord user ID if not set
            if (user.getDiscordUserId() == null && discordService.isEnabled()) {
                discordService.findDiscordUserId(user.getDiscordNickname())
                        .ifPresent(user::setDiscordUserId);
            }

            // Auto-sync Discord avatar if missing
            if ((user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) && user.getDiscordUserId() != null) {
                String avatarUrl = discordService.syncDiscordAvatar(user.getDiscordUserId());
                if (avatarUrl != null) {
                    user.setAvatarUrl(avatarUrl);
                }
            }

            userRepository.save(user);
        });
    }
}
