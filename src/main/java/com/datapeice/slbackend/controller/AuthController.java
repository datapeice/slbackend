package com.datapeice.slbackend.controller;

import com.datapeice.slbackend.dto.SignInBody;
import com.datapeice.slbackend.dto.SignUpBody;
import com.datapeice.slbackend.dto.VerifyEmailRequest;
import com.datapeice.slbackend.dto.ForgotPasswordRequest;
import com.datapeice.slbackend.dto.ResetPasswordRequest;
import com.datapeice.slbackend.entity.User;
import com.datapeice.slbackend.entity.UserRole;
import com.datapeice.slbackend.repository.UserRepository;
import com.datapeice.slbackend.security.JwtCore;
import com.datapeice.slbackend.service.EmailService;
import com.datapeice.slbackend.service.GeoIpService;
import com.datapeice.slbackend.service.RecaptchaService;
import com.datapeice.slbackend.service.RateLimitService;
import com.datapeice.slbackend.service.TotpService;
import com.datapeice.slbackend.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final JwtCore jwtCore;
    private final EmailService emailService;
    private final RecaptchaService recaptchaService;
    private final RateLimitService rateLimitService;
    private final TotpService totpService;
    private final UserService userService;
    private final GeoIpService geoIpService;

    @Value("${email.verification.expiration}")
    private long emailVerificationExpiration;

    @Value("${app.recaptcha.enabled:true}")
    private boolean recaptchaEnabled;

    @Value("${app.email.enabled:true}")
    private boolean emailEnabled;

    @Value("${app.auto-verify-email:false}")
    private boolean autoVerifyEmail;

    public AuthController(AuthenticationManager authenticationManager,
                         UserRepository userRepository,
                         BCryptPasswordEncoder bCryptPasswordEncoder,
                         JwtCore jwtCore,
                         EmailService emailService,
                         RecaptchaService recaptchaService,
                         RateLimitService rateLimitService,
                         TotpService totpService,
                         UserService userService,
                         GeoIpService geoIpService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.jwtCore = jwtCore;
        this.emailService = emailService;
        this.recaptchaService = recaptchaService;
        this.rateLimitService = rateLimitService;
        this.totpService = totpService;
        this.userService = userService;
        this.geoIpService = geoIpService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody SignUpBody body, HttpServletRequest request) {
        String ipAddress = getClientIP(request);
        String userAgent = request.getHeader("User-Agent");

        // Проверка User-Agent
        if (userAgent == null || userAgent.isBlank() || userAgent.length() < 10) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid User-Agent"));
        }

        // Rate limiting
        if (!rateLimitService.checkAuthRateLimit(ipAddress)) {
            return ResponseEntity.status(429).body(Map.of("error", "Слишком много попыток. Попробуйте позже."));
        }

        // Проверка reCAPTCHA
        if (recaptchaEnabled) {
            String token = body.getRecaptchaToken();
            if (token == null || token.isBlank()) {
                logger.warn("reCAPTCHA token is missing. Request from IP: {}", ipAddress);
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "reCAPTCHA token обязателен",
                    "hint", "Frontend должен получить токен через grecaptcha.execute()"
                ));
            }

            if (!recaptchaService.verifyRecaptcha(token, "register")) {
                logger.warn("reCAPTCHA verification failed for IP: {}", ipAddress);
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "reCAPTCHA проверка не пройдена",
                    "hint", "Возможно, вы робот или токен истек"
                ));
            }
        }

        // Email rate limiting
        if (emailEnabled && !rateLimitService.checkEmailRateLimit(ipAddress)) {
            return ResponseEntity.status(429).body(Map.of("error", "Превышен лимит отправки email. Попробуйте позже."));
        }

        if (userRepository.existsByUsername(body.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Имя пользователя уже занято"));
        }

        if (userRepository.existsByEmail(body.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email уже используется"));
        }

        if (userRepository.existsByDiscordNickname(body.getDiscordNickname())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Discord никнейм уже используется"));
        }

        if (userRepository.existsByMinecraftNickname(body.getMinecraftNickname())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Minecraft никнейм уже используется"));
        }

        User user = new User();
        user.setUsername(body.getUsername());
        user.setPassword(bCryptPasswordEncoder.encode(body.getPassword()));
        user.setEmail(body.getEmail());
        user.setDiscordNickname(body.getDiscordNickname());
        user.setMinecraftNickname(body.getMinecraftNickname());
        user.setRole(UserRole.ROLE_USER);
        // Security logging
        user.setRegistrationIp(geoIpService.formatIpWithGeo(ipAddress));
        user.setRegistrationUserAgent(userAgent != null && userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent);

        // В dev режиме автоматически верифицируем email
        if (autoVerifyEmail) {
            user.setEmailVerified(true);
        } else {
            user.setEmailVerified(false);
            // Генерируем токен подтверждения email
            String verificationToken = generateVerificationToken();
            user.setEmailVerificationToken(verificationToken);
            user.setEmailVerificationTokenExpiry(System.currentTimeMillis() + emailVerificationExpiration);

            userRepository.save(user);

            // Отправляем письмо подтверждения
            if (emailEnabled) {
                try {
                    emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), verificationToken);
                } catch (Exception e) {
                    // Логируем ошибку, но не удаляем пользователя
                    // В dev режиме это не критично
                }
            }

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Регистрация успешна! Проверьте ваш email для подтверждения аккаунта."
            ));
        }

        // Если auto-verify включен, сразу логиним пользователя
        userRepository.save(user);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities()
        );
        String jwtToken = jwtCore.generateToken(authentication);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Регистрация успешна!",
                "token", jwtToken
        ));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        logger.info("Email verification attempt with token: {}", request.getToken());

        User user = userRepository.findByEmailVerificationToken(request.getToken())
                .orElse(null);

        if (user == null) {
            logger.warn("Token not found in database: {}", request.getToken());

            // Проверяем, может быть email уже подтвержден (токен был использован ранее)
            // Это нормальная ситуация при повторных запросах от frontend
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Неверный токен подтверждения",
                "code", "INVALID_TOKEN",
                "hint", "Возможно, email уже подтвержден. Попробуйте войти."
            ));
        }

        logger.info("User found for token: {}, expiry: {}, current time: {}",
            user.getUsername(), user.getEmailVerificationTokenExpiry(), System.currentTimeMillis());

        // Проверяем, не подтвержден ли уже email
        if (user.isEmailVerified()) {
            logger.info("Email already verified for user: {}, returning success", user.getUsername());

            // Возвращаем успех, генерируем новый токен
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    user, null, user.getAuthorities()
            );
            String jwtToken = jwtCore.generateToken(authentication);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Email уже подтвержден",
                    "token", jwtToken,
                    "alreadyVerified", true
            ));
        }

        if (user.getEmailVerificationTokenExpiry() < System.currentTimeMillis()) {
            logger.warn("Token expired for user: {}", user.getUsername());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Токен подтверждения истек",
                "code", "TOKEN_EXPIRED",
                "hint", "Запросите новое письмо подтверждения"
            ));
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);
        userRepository.save(user);

        logger.info("Email verified successfully for user: {}", user.getUsername());

        // Автоматический вход после подтверждения
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities()
        );
        String jwtToken = jwtCore.generateToken(authentication);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Email подтвержден успешно!",
                "token", jwtToken,
                "alreadyVerified", false
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody SignInBody body, HttpServletRequest request) {
        String ipAddress = getClientIP(request);
        String userAgent = request.getHeader("User-Agent");

        // Проверка User-Agent
        if (userAgent == null || userAgent.isBlank() || userAgent.length() < 10) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid User-Agent"));
        }

        // Rate limiting
        if (!rateLimitService.checkAuthRateLimit(ipAddress)) {
            return ResponseEntity.status(429).body(Map.of("error", "Слишком много попыток. Попробуйте позже."));
        }

        try {
            // Сначала загружаем пользователя для проверки TOTP
            User user = userRepository.findByUsername(body.getUsername())
                    .orElseThrow(() -> new RuntimeException("Неверное имя пользователя или пароль"));

            // Проверяем TOTP если включен
            if (user.isTotpEnabled()) {
                if (body.getTotpCode() == null || body.getTotpCode().isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "TOTP code required",
                            "totpRequired", true
                    ));
                }

                if (!totpService.verifyCode(user.getTotpSecret(), body.getTotpCode())) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Неверный TOTP код"));
                }
            }

            // Аутентификация (даже для неподтвержденных пользователей)
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(body.getUsername(), body.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwtToken = jwtCore.generateToken(authentication);

            // Record login IP/UA for security logging
            final String finalUa = userAgent != null && userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
            userService.recordLogin(user.getUsername(), ipAddress, finalUa);

            // Возвращаем токен + статус верификации
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "token", jwtToken,
                    "emailVerified", user.isEmailVerified(),
                    "username", user.getUsername(),
                    "isPlayer", user.isPlayer()
            ));
        } catch (Exception e) {
            logger.error("Authentication failed for user: {}", body.getUsername(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "Неверное имя пользователя или пароль"));
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String email = request.get("email");
        String ipAddress = getClientIP(httpRequest);

        if (email == null || email.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email обязателен"));
        }

        // Rate limiting для email
        if (emailEnabled && !rateLimitService.checkEmailRateLimit(ipAddress)) {
            return ResponseEntity.status(429).body(Map.of("error", "Превышен лимит отправки email. Попробуйте позже."));
        }

        User user = userRepository.findByEmail(email)
                .orElse(null);

        if (user == null) {
            // Не раскрываем, существует ли email
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Если email существует, письмо будет отправлено."
            ));
        }

        if (user.isEmailVerified()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email уже подтвержден"));
        }

        // Генерируем новый токен
        String verificationToken = generateVerificationToken();
        user.setEmailVerificationToken(verificationToken);
        user.setEmailVerificationTokenExpiry(System.currentTimeMillis() + emailVerificationExpiration);
        userRepository.save(user);

        // Отправляем письмо
        if (emailEnabled) {
            try {
                emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), verificationToken);
            } catch (Exception e) {
                // Логируем, но не показываем ошибку пользователю
            }
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Письмо подтверждения отправлено на ваш email"
        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        userService.processForgotPassword(request.getEmail());
        // Always return OK to avoid user enumeration
        return ResponseEntity.ok(Map.of("message", "Если аккаунт с таким email существует, письмо для сброса пароля отправлено."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        logger.info("Resetting password for token: {}", request.getToken());
        try {
            userService.resetPassword(request.getToken(), request.getNewPassword());
            return ResponseEntity.ok(Map.of("message", "Пароль успешно изменен."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String getClientIP(HttpServletRequest request) {
        // Cloudflare passes real IP in CF-Connecting-IP
        String cfIp = request.getHeader("CF-Connecting-IP");
        if (cfIp != null && !cfIp.isBlank()) {
            return cfIp.trim();
        }
        // Fallback: X-Forwarded-For (other proxies/load balancers)
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        // Last resort: direct connection IP
        return request.getRemoteAddr();
    }

    private String generateVerificationToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        StringBuilder token = new StringBuilder();
        for (byte b : bytes) {
            token.append(String.format("%02x", b));
        }
        return token.toString();
    }
}

