package com.datapeice.slbackend.service;

import com.datapeice.slbackend.dto.ApplicationResponse;
import com.datapeice.slbackend.dto.CreateApplicationRequest;
import com.datapeice.slbackend.dto.UpdateApplicationStatusRequest;
import com.datapeice.slbackend.entity.Application;
import com.datapeice.slbackend.entity.ApplicationStatus;
import com.datapeice.slbackend.entity.User;
import com.datapeice.slbackend.repository.ApplicationRepository;
import com.datapeice.slbackend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationService.class);

    private final ApplicationRepository applicationRepository;
    private final RecaptchaService recaptchaService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final DiscordService discordService;

    public ApplicationService(ApplicationRepository applicationRepository, RecaptchaService recaptchaService, UserRepository userRepository, EmailService emailService, DiscordService discordService) {
        this.applicationRepository = applicationRepository;
        this.recaptchaService = recaptchaService;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.discordService = discordService;
    }

    @Transactional
    public ApplicationResponse createApplication(User user, CreateApplicationRequest request) {
        logger.info("Application creation attempt by user: {}", user.getUsername());

        // Проверяем подтвержден ли email
        if (!user.isEmailVerified()) {
            logger.warn("User {} tried to create application without verifying email", user.getUsername());
            throw new IllegalStateException("Вы должны подтвердить свой email перед подачей заявки");
        }

        // Проверяем reCAPTCHA
        String recaptchaToken = request.getRecaptchaToken();
        if (recaptchaToken == null || recaptchaToken.isBlank()) {
            logger.warn("reCAPTCHA token missing for user: {}", user.getUsername());
            throw new IllegalStateException("reCAPTCHA token обязателен для отправки заявки");
        }

        if (!recaptchaService.verifyRecaptcha(recaptchaToken, "submit_application")) {
            logger.warn("reCAPTCHA verification failed for user: {}", user.getUsername());
            throw new IllegalStateException("reCAPTCHA проверка не пройдена. Возможно, вы робот или токен истек");
        }

        logger.info("reCAPTCHA verified successfully for user: {}", user.getUsername());

        // Проверяем наличие пользователя в Discord сервере
        if (discordService.isEnabled()) {
            boolean inGuild = discordService.isMemberInGuild(user.getDiscordNickname());
            if (!inGuild) {
                logger.warn("User {} is not in Discord guild", user.getUsername());
                throw new IllegalStateException("Вы должны быть участником нашего Discord сервера, чтобы подать заявку. Ник: " + user.getDiscordNickname());
            }
            // Try to save Discord user ID for future notifications
            if (user.getDiscordUserId() == null) {
                Optional<String> discordId = discordService.findDiscordUserId(user.getDiscordNickname());
                discordId.ifPresent(id -> {
                    user.setDiscordUserId(id);
                    userRepository.save(user);
                });
            }
        }

        // Проверяем, нет ли уже активной заявки
        List<Application> existingPending = applicationRepository.findAllByUserIdAndStatus(
                user.getId(), ApplicationStatus.PENDING
        );

        if (!existingPending.isEmpty()) {
            throw new IllegalStateException("У вас уже есть активная заявка в статусе PENDING");
        }

        Application application = new Application();
        application.setUser(user);
        application.setFirstName(request.getFirstName());
        application.setLastName(request.getLastName());
        application.setWhyUs(request.getWhyUs());
        application.setSource(request.getSource());
        application.setMakeContent(request.isMakeContent());
        application.setAdditionalInfo(request.getAdditionalInfo());
        application.setSelfRating(request.getSelfRating());
        application.setStatus(ApplicationStatus.PENDING);

        Application saved = applicationRepository.save(application);

        // Notify user via Discord DM
        if (user.getDiscordUserId() != null) {
            discordService.sendDirectMessage(user.getDiscordUserId(),
                    "**StoryLegends** — Ваша заявка на вступление была успешно отправлена! " +
                    "Мы рассмотрим её в ближайшее время.\n" + "***С уважением, <:slteam:1244336090928906351>***");
        }

        return mapToResponse(saved);
    }

    public List<ApplicationResponse> getMyApplication(User user) {
        List<Application> applications = applicationRepository.findAllByUserId(user.getId());

        if (applications.isEmpty()) {
            return List.of();
        }

        // Возвращаем список заявок, отсортированный по дате создания (новые сверху)
        return applications.stream()
                .sorted((a1, a2) -> a2.getCreatedAt().compareTo(a1.getCreatedAt()))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<ApplicationResponse> getAllApplications() {
        return applicationRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<ApplicationResponse> getApplicationsByStatus(ApplicationStatus status) {
        return applicationRepository.findAllByStatus(status).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ApplicationResponse updateApplicationStatus(Long applicationId, UpdateApplicationStatusRequest request) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Заявка не найдена"));

        application.setStatus(request.getStatus());
        application.setAdminComment(request.getAdminComment());

        if (request.getStatus() == ApplicationStatus.ACCEPTED) {
            User user = application.getUser();
            String reason = request.getAdminComment() != null ? request.getAdminComment() : "Отсутствует";

            user.setPlayer(true);
            // Resolve Discord user ID if not set (for DM)
            if (user.getDiscordUserId() == null && discordService.isEnabled()) {
                discordService.findDiscordUserId(user.getDiscordNickname())
                        .ifPresent(user::setDiscordUserId);
            }
            userRepository.save(user);
            emailService.sendApplicationAcceptedEmail(user.getEmail(), user.getUsername(), user.getDiscordNickname());
            // Send DM only — @SL role is managed ONLY via isPlayer in admin panel
            if (user.getDiscordUserId() != null) {
                discordService.sendDirectMessage(user.getDiscordUserId(),
                        "**Приветствую!**\n" +
                                "Ваша заявка на вступление на сервер **StoryLegends** была принята!\n" +
                                "Комментарий от администрации: *" + reason + "*\n" +
                                "Добро пожаловать на наш сервер, дабы **начать играть** вам нужно **прочитать** канал <#1229044440178626660>.\n" +
                                "Так-же если вы ещё не ознакомилсь с [правилами](https://www.storylegends.xyz/rules) сервера, то обязательно это сделайте!\n" +
                                "**Удачной игры**\n" +
                                "***С уважением, <:slteam:1244336090928906351>***");
            }
        } else if (request.getStatus() == ApplicationStatus.REJECTED) {
            User user = application.getUser();
            emailService.sendApplicationRejectedEmail(user.getEmail(), user.getUsername(), request.getAdminComment());
            // Send rejection DM
            if (user.getDiscordUserId() != null) {
                String reason = request.getAdminComment() != null ? request.getAdminComment() : "Причина не указана";
                discordService.sendDirectMessage(user.getDiscordUserId(),
                        "**Приветствую!**\n" +
                                "Ваша заявка на вступление на сервер **StoryLegends** к сожелению было **отклонена**!\n" +
                                "Комментарий от администрации: *" + reason + "* \n***С уважением, <:slteam:1244336090928906351>***");
            }
        }

        Application updated = applicationRepository.save(application);
        return mapToResponse(updated);
    }

    private ApplicationResponse mapToResponse(Application application) {
        ApplicationResponse response = new ApplicationResponse();
        response.setId(application.getId());
        response.setFirstName(application.getFirstName());
        response.setLastName(application.getLastName());
        response.setWhyUs(application.getWhyUs());
        response.setSource(application.getSource());
        response.setMakeContent(application.isMakeContent());
        response.setAdditionalInfo(application.getAdditionalInfo());
        response.setSelfRating(application.getSelfRating());
        response.setStatus(application.getStatus());
        response.setAdminComment(application.getAdminComment());
        response.setCreatedAt(application.getCreatedAt());

        ApplicationResponse.UserSummary userSummary = new ApplicationResponse.UserSummary();
        userSummary.setId(application.getUser().getId());
        userSummary.setUsername(application.getUser().getUsername());
        userSummary.setEmail(application.getUser().getEmail());
        userSummary.setDiscordNickname(application.getUser().getDiscordNickname());
        userSummary.setMinecraftNickname(application.getUser().getMinecraftNickname());
        userSummary.setAvatarUrl(application.getUser().getAvatarUrl());
        response.setUser(userSummary);

        return response;
    }
}
