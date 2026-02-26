package com.datapeice.slbackend.service;

import com.datapeice.slbackend.dto.AdminCreateUserRequest;
import com.datapeice.slbackend.dto.AdminUpdateUserRequest;
import com.datapeice.slbackend.dto.BadgeResponse;
import com.datapeice.slbackend.dto.UpdateUserRequest;
import com.datapeice.slbackend.dto.UserResponse;
import com.datapeice.slbackend.entity.User;
import com.datapeice.slbackend.entity.UserRole;
import com.datapeice.slbackend.entity.SiteSettings;
import com.datapeice.slbackend.repository.UserRepository;
import com.datapeice.slbackend.repository.ApplicationRepository;
import com.datapeice.slbackend.repository.WarningRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final DiscordService discordService;
    private final GeoIpService geoIpService;
    private final FileStorageService fileStorageService;
    private final SiteSettingsService siteSettingsService;
    private final AuditLogService auditLogService;
    private final ModerationService moderationService;
    private final ApplicationRepository applicationRepository;
    private final WarningRepository warningRepository;

    public UserService(UserRepository userRepository,
            AuditLogService auditLogService,
            DiscordService discordService,
            FileStorageService fileStorageService,
            EmailService emailService,
            SiteSettingsService siteSettingsService,
            ModerationService moderationService,
            GeoIpService geoIpService,
            BCryptPasswordEncoder passwordEncoder,
            ApplicationRepository applicationRepository,
            WarningRepository warningRepository) {
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.discordService = discordService;
        this.fileStorageService = fileStorageService;
        this.emailService = emailService;
        this.siteSettingsService = siteSettingsService;
        this.moderationService = moderationService;
        this.geoIpService = geoIpService;
        this.passwordEncoder = passwordEncoder;
        this.applicationRepository = applicationRepository;
        this.warningRepository = warningRepository;
    }

    private SiteSettings getSiteSettings() {
        return siteSettingsService.getSettings();
    }

    @Transactional
    public UserResponse getUserProfile(User user) {
        // Re-fetch from DB to ensure session is open for EAGER collections
        User fresh = userRepository.findById(user.getId()).orElse(user);
        // Auto-sync Discord avatar if missing or if it's a broken local path (storage
        // disabled)
        boolean isLocalPath = fresh.getAvatarUrl() != null && !fresh.getAvatarUrl().startsWith("http");
        if ((fresh.getAvatarUrl() == null || fresh.getAvatarUrl().isBlank()
                || (isLocalPath && !fileStorageService.isEnabled()))
                && fresh.getDiscordUserId() != null) {
            syncDiscordAvatarForUser(fresh);
        }
        return mapToResponse(fresh);
    }

    @Transactional
    public void syncDiscordAvatarForUser(User user) {
        if (user.getDiscordUserId() == null)
            return;
        String url = discordService.syncDiscordAvatar(user.getDiscordUserId());
        if (url != null) {
            String oldAvatar = user.getAvatarUrl();
            if (url != null && !url.equals(oldAvatar)) {
                if (oldAvatar != null && !oldAvatar.startsWith("http")) {
                    fileStorageService.deleteFile(oldAvatar);
                }
                user.setAvatarUrl(url);
                userRepository.save(user);
                auditLogService.logAction(user.getId(), user.getUsername(), "USER_UPDATE_AVATAR",
                        "Синхронизировал аватар из Discord", user.getId(), user.getUsername());
            }
        }
    }

    @Transactional
    public UserResponse updateUserProfile(User user, UpdateUserRequest request) {
        // Если request null - обновляем только то что уже изменено в user (например
        // аватар)
        if (request == null) {
            User updated = userRepository.save(user);
            return mapToResponse(updated);
        }

        java.util.List<String> changes = new java.util.ArrayList<>();

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email уже используется");
            }
            changes.add("Email: " + user.getEmail() + " -> " + request.getEmail());
            user.setEmail(request.getEmail());
        }

        if (request.getDiscordNickname() != null && !request.getDiscordNickname().equals(user.getDiscordNickname())) {
            if (user.isPlayer()) {
                throw new IllegalArgumentException(
                        "Игрокам запрещено изменять Discord никнейм вручную. Используйте только кнопку привязки (OAuth2).");
            }
            if (!request.getDiscordNickname().isBlank()
                    && userRepository.existsByDiscordNickname(request.getDiscordNickname())) {
                throw new IllegalArgumentException("Discord никнейм уже используется");
            }
            changes.add("Discord Nick: " + user.getDiscordNickname() + " -> " + request.getDiscordNickname());
            user.setDiscordNickname(request.getDiscordNickname().isBlank() ? null : request.getDiscordNickname());
            // When discord nickname changes, reset verification - user must re-verify via
            // OAuth
            user.setDiscordVerified(false);
            user.setDiscordUserId(null);
        }

        if (request.getMinecraftNickname() != null
                && !request.getMinecraftNickname().equals(user.getMinecraftNickname())) {
            if (user.isPlayer()) {
                throw new IllegalArgumentException(
                        "Игрокам запрещено изменять Minecraft никнейм. Обратитесь к администратору.");
            }
            if (!request.getMinecraftNickname().isBlank()
                    && userRepository.existsByMinecraftNickname(request.getMinecraftNickname())) {
                throw new IllegalArgumentException("Minecraft никнейм уже используется");
            }
            changes.add("Minecraft Nick: " + user.getMinecraftNickname() + " -> " + request.getMinecraftNickname());
            user.setMinecraftNickname(request.getMinecraftNickname().isBlank() ? null : request.getMinecraftNickname());
        }

        if (request.getBio() != null && !request.getBio().equals(user.getBio())) {
            if (moderationService.isTextToxic(request.getBio())) {
                throw new IllegalArgumentException(
                        "Текст 'О себе' нарушает правила платформы (токсичность/недопустимый контент). Пожалуйста, напишите другой текст.");
            }
            changes.add(String.format("Bio updated (%d characters)", request.getBio().length()));
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
            changes.add("Password changed");
        }

        User updated = userRepository.save(user);

        if (!changes.isEmpty()) {
            auditLogService.logAction(user.getId(), user.getUsername(), "USER_UPDATE_PROFILE",
                    "Обновил профиль: " + String.join(", ", changes), user.getId(), user.getUsername());
        }

        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        // Возвращаем только пользователей с подтвержденным email И принятой заявкой
        // (isPlayer = true) И находящихся на сервере Discord
        return userRepository.findAll().stream()
                .filter(User::isPlayer)
                .map(this::mapToResponse)
                .filter(UserResponse::isInDiscordServer)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsersForAdmin(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(u -> mapToResponse(u, true));
    }

    @Transactional
    public UserResponse banUser(Long userId, String reason, Long adminId, String adminName) {
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

        SiteSettings settings = getSiteSettings();

        if (settings.isSendDiscordDmOnBan() && user.getDiscordUserId() != null && discordService.isEnabled()) {
            discordService.removeSlRole(user.getDiscordUserId());
            discordService.sendDirectMessage(user.getDiscordUserId(),
                    "🚫 **StoryLegends** — Ваш аккаунт был **заблокирован** администрацией.\n" +
                            "**Причина:** " + (reason != null ? reason : "Причина не указана") + "\n" +
                            "**Модератор:** " + adminName + "\n" +
                            "***С уважением, <:slteam:1244336090928906351>***");
        } else if (!settings.isSendDiscordDmOnBan() && user.getDiscordUserId() != null && discordService.isEnabled()) {
            // Still remove the role even if DM is disabled
            discordService.removeSlRole(user.getDiscordUserId());
        }

        if (settings.isSendEmailOnBan()) {
            emailService.sendBanEmail(user.getEmail(), user.getUsername(), reason);
        }

        auditLogService.logAction(adminId, adminName, "ADMIN_BAN_USER", "Забанил пользователя. Причина: " + reason,
                user.getId(), user.getUsername());

        return mapToResponse(updated);
    }

    @Transactional
    public UserResponse unbanUser(Long userId, Long adminId, String adminName) {
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
                            "**Модератор:** " + adminName + "\n" +
                            "Добро пожаловать обратно!\n" +
                            "***С уважением, <:slteam:1244336090928906351>***");
        }

        auditLogService.logAction(adminId, adminName, "ADMIN_UNBAN_USER", "Разбанил пользователя", user.getId(),
                user.getUsername());

        return mapToResponse(updated);
    }

    // Admin methods

    @Transactional
    public void resetUserPassword(Long userId, Long adminId, String adminName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        String newPassword = generateRandomPassword();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Отправляем email с новым паролем
        emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), newPassword);

        auditLogService.logAction(adminId, adminName, "ADMIN_RESET_PASSWORD", "Сброшен пароль пользователя",
                user.getId(), user.getUsername());
    }

    @Transactional
    public void resetAllUsersSeason(Long adminId, String adminName) {
        userRepository.resetSeasonForAll();
        auditLogService.logAction(adminId, adminName, "ADMIN_RESET_SEASON",
                "Сброшен статус сезона. Все игроки могут снова подать заявки.", null, null);
    }

    @Transactional
    public UserResponse adminUpdateUser(Long userId, AdminUpdateUserRequest request, Long adminId, String adminName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        java.util.List<String> changes = new java.util.ArrayList<>();

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new IllegalArgumentException("Имя пользователя уже занято");
            }
            changes.add("Username: " + user.getUsername() + " -> " + request.getUsername());
            user.setUsername(request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email уже используется");
            }
            changes.add("Email: " + user.getEmail() + " -> " + request.getEmail());
            user.setEmail(request.getEmail());
            // Reset email verification — user must confirm new email
            user.setEmailVerified(false);
            String verificationToken = java.util.UUID.randomUUID().toString();
            user.setEmailVerificationToken(verificationToken);
            user.setEmailVerificationTokenExpiry(System.currentTimeMillis() + 86400000L); // 24h
            emailService.sendVerificationEmail(request.getEmail(), user.getUsername(), verificationToken);
        }

        // Discord Sync Logic
        if (request.getDiscordNickname() != null) {
            boolean nickChanged = !request.getDiscordNickname().equals(user.getDiscordNickname());
            if (nickChanged) {
                if (userRepository.existsByDiscordNickname(request.getDiscordNickname())) {
                    throw new IllegalArgumentException("Discord никнейм уже используется");
                }
                changes.add("Discord Nick: " + user.getDiscordNickname() + " -> " + request.getDiscordNickname());
                user.setDiscordNickname(request.getDiscordNickname());
            }

            if (discordService.isEnabled()) {
                // If ID is missing or nick changed, try to find current ID
                if (user.getDiscordUserId() == null || nickChanged) {
                    discordService.findDiscordUserId(user.getDiscordNickname())
                            .ifPresent(user::setDiscordUserId);
                }

                if (user.getDiscordUserId() != null) {
                    // Sync avatar if it's not a local file, OR if it's currently a placeholder/null
                    String currentAvatar = user.getAvatarUrl();
                    boolean isLocalFile = currentAvatar != null && !currentAvatar.startsWith("http")
                            && currentAvatar.length() > 5;

                    if (!isLocalFile || currentAvatar == null || currentAvatar.isBlank()) {
                        syncDiscordAvatarForUser(user);
                    }
                }
            }
        }

        if (request.getMinecraftNickname() != null
                && !request.getMinecraftNickname().equals(user.getMinecraftNickname())) {
            if (userRepository.existsByMinecraftNickname(request.getMinecraftNickname())) {
                throw new IllegalArgumentException("Minecraft никнейм уже используется");
            }
            changes.add("Minecraft Nick: " + user.getMinecraftNickname() + " -> " + request.getMinecraftNickname());
            user.setMinecraftNickname(request.getMinecraftNickname());
        }

        if (Boolean.TRUE.equals(request.getUnlinkDiscord())) {
            if (user.getDiscordUserId() != null && discordService.isEnabled()) {
                discordService.removeSlRole(user.getDiscordUserId());
            }
            changes.add("Unlinked Discord (" + user.getDiscordNickname() + ")");
            user.setDiscordNickname(null);
            user.setDiscordUserId(null);
            user.setDiscordVerified(false);
        }

        if (request.getBio() != null && !request.getBio().equals(user.getBio())) {
            changes.add(String.format("Bio updated (%d characters)", request.getBio().length()));
            user.setBio(request.getBio());
        }

        if (request.getRole() != null) {
            try {
                UserRole newRole = UserRole.valueOf(request.getRole());
                if (newRole != user.getRole()) {
                    changes.add("Role: " + user.getRole() + " -> " + newRole);
                    user.setRole(newRole);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Неверная роль");
            }
        }

        if (request.getIsPlayer() != null) {
            boolean wasPlayer = user.isPlayer();
            boolean nowPlayer = request.getIsPlayer();

            if (wasPlayer != nowPlayer) {
                changes.add("IsPlayer: " + wasPlayer + " -> " + nowPlayer);
                user.setPlayer(nowPlayer);

                // Sync @SL Discord role
                if (discordService.isEnabled()) {
                    // Resolve Discord user ID if not yet saved
                    if (user.getDiscordUserId() == null) {
                        discordService.findDiscordUserId(user.getDiscordNickname())
                                .ifPresent(user::setDiscordUserId);
                    }
                    if (user.getDiscordUserId() != null) {
                        if (nowPlayer) {
                            discordService.assignSlRole(user.getDiscordUserId());
                            discordService.sendDirectMessage(user.getDiscordUserId(),
                                    "**Приветствую!**\n" +
                                            "Вам выдана роль @SL на сервере StoryLegends\n" +
                                            "Добро пожаловать на наш сервер, дабы **начать играть** вам нужно **прочитать** канал <#1229044440178626660>.\n"
                                            +
                                            "Так-же если вы ещё не ознакомилсь с [правилами](https://www.storylegends.xyz/rules) сервера, то обязательно это сделайте!\n"
                                            +
                                            "**Модератор:** " + adminName + "\n" +
                                            "***С уважением, <:slteam:1244336090928906351>***");
                        } else {
                            discordService.removeSlRole(user.getDiscordUserId());
                            discordService.sendDirectMessage(user.getDiscordUserId(),
                                    "**StoryLegends** — Ваш статус игрока был отозван администрацией. Роль @SL удалена.\n"
                                            +
                                            "**Модератор:** " + adminName + "\n" +
                                            "**С уважением, <:slteam:1244336090928906351>**");
                        }
                    }
                }
            }
        }

        User updated = userRepository.save(user);

        if (!changes.isEmpty()) {
            auditLogService.logAction(adminId, adminName, "ADMIN_UPDATE_USER",
                    "Админ обновил данные: " + String.join(", ", changes),
                    user.getId(), user.getUsername());
        }

        return mapToResponse(updated, true);
    }

    @Transactional
    public void deleteUser(Long userId, Long adminId, String adminName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        // Delete related entities manually to avoid FK violations
        applicationRepository.deleteAllByUserId(userId);
        warningRepository.deleteAllByUserId(userId);
        warningRepository.deleteAllByIssuedById(userId);

        userRepository.delete(user);
        auditLogService.logAction(adminId, adminName, "ADMIN_DELETE_USER", "Админ удалил пользователя", user.getId(),
                user.getUsername());
    }

    @Transactional
    public UserResponse createUser(AdminCreateUserRequest request, Long adminId, String adminName) {
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

        if (request.getRole() != null) {
            try {
                user.setRole(UserRole.valueOf(request.getRole()));
            } catch (IllegalArgumentException e) {
                user.setRole(UserRole.ROLE_USER);
            }
        } else {
            user.setRole(UserRole.ROLE_USER);
        }

        user.setPlayer(request.getIsPlayer() != null ? request.getIsPlayer() : false);
        user.setEmailVerified(request.isEmailVerified());

        User saved = userRepository.save(user);

        // Sync Discord Avatar & Role if available
        if (saved.getDiscordNickname() != null && discordService.isEnabled()) {
            discordService.findDiscordUserId(saved.getDiscordNickname())
                    .ifPresent(discordId -> {
                        saved.setDiscordUserId(discordId);
                        userRepository.save(saved);
                        syncDiscordAvatarForUser(saved);

                        if (saved.isPlayer()) {
                            discordService.assignSlRole(discordId);
                            discordService.sendDirectMessage(discordId,
                                    "**Приветствую!**\n" +
                                            "Вам выдана роль @SL на сервере StoryLegends\n" +
                                            "Добро пожаловать на наш сервер, дабы **начать играть** вам нужно **прочитать** канал <#1229044440178626660>.\n"
                                            +
                                            "Так-же если вы ещё не ознакомилсь с [правилами](https://www.storylegends.xyz/rules) сервера, то обязательно это сделайте!\n"
                                            +
                                            "**Модератор:** " + adminName + "\n" +
                                            "***С уважением, <:slteam:1244336090928906351>***");
                        }
                    });
        }

        auditLogService.logAction(adminId, adminName, "ADMIN_CREATE_USER", "Админ создал нового пользователя",
                saved.getId(), saved.getUsername());
        return mapToResponse(saved, true);
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

            auditLogService.logAction(user.getId(), user.getUsername(), "USER_FORGOT_PASSWORD",
                    "Запросил восстановление пароля через email", user.getId(), user.getUsername());
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetPasswordToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Неверный или истекший токен восстановления"));

        if (user.getResetPasswordTokenExpiry() == null
                || user.getResetPasswordTokenExpiry() < System.currentTimeMillis()) {
            throw new IllegalArgumentException("Срок действия токена истек");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        userRepository.save(user);

        auditLogService.logAction(user.getId(), user.getUsername(), "USER_RESET_PASSWORD",
                "Успешно изменил пароль через токен восстановления", user.getId(), user.getUsername());
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

    public UserResponse getUserById(Long userId) {
        return userRepository.findById(userId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    /**
     * Resolves an avatar URL or object key to a viewable URL.
     * - Plain object key (e.g., "avatars/uuid.png") → generates presigned/public
     * URL
     * - Old full S3/MinIO URL → extracts object key, then generates fresh URL
     * - External URL (Discord CDN etc.) → returned as-is
     */
    private String resolveAvatarUrl(String avatarUrl, String username) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return username != null && !username.isBlank() ? username.substring(0, 1).toUpperCase() : null;
        }

        String resolved = avatarUrl;
        if (!avatarUrl.startsWith("http://") && !avatarUrl.startsWith("https://")) {
            // Stored as object key — generate fresh URL
            try {
                resolved = fileStorageService.resolveUrl(avatarUrl);
                // If storage is disabled, resolveUrl returns objectKey (e.g. "avatars/...")
                // which leads to 404. In this case, we prefer null/letter fallback.
                if (resolved != null && !resolved.startsWith("http")) {
                    resolved = null;
                }
            } catch (Exception e) {
                resolved = null;
            }
        } else {
            // It's a full URL — try to extract object key and re-resolve (handles expired
            // presigned URLs)
            try {
                String objectKey = fileStorageService.extractObjectKey(avatarUrl);
                if (objectKey != null) {
                    resolved = fileStorageService.resolveUrl(objectKey);
                }
            } catch (Exception ignored) {
            }
        }

        if (resolved == null || resolved.isBlank() || (!resolved.startsWith("http") && resolved.length() > 1)) {
            return username != null && !username.isBlank() ? username.substring(0, 1).toUpperCase() : null;
        }

        return resolved;
    }

    private UserResponse mapToResponse(User user, boolean includeSecurityInfo) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setDiscordNickname(user.getDiscordNickname());
        response.setMinecraftNickname(user.getMinecraftNickname());
        response.setRole(user.getRole());
        response.setAvatarUrl(resolveAvatarUrl(user.getAvatarUrl(), user.getUsername()));
        response.setBanned(user.isBanned());
        response.setBanReason(user.getBanReason());
        response.setEmailVerified(user.isEmailVerified());
        response.setTotpEnabled(user.isTotpEnabled());
        response.setBio(user.getBio());
        response.setPlayer(user.isPlayer());
        response.setInSeason(user.isInSeason());
        response.setDiscordUserId(user.getDiscordUserId());
        response.setDiscordVerified(user.isDiscordVerified());

        response.setInDiscordServer(user.isInDiscord() ||
                discordService.isMemberInGuildCached(user.getDiscordUserId(), user.getDiscordNickname()));

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

            // Log login action
            auditLogService.logAction(user.getId(), user.getUsername(), "USER_LOGIN",
                    String.format("Вход в систему (IP: %s, UA: %s)", geoIp, userAgent),
                    user.getId(), user.getUsername());
        });
    }
}
