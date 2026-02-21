package com.datapeice.slbackend.service;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class RecaptchaService {

    private static final Logger logger = LoggerFactory.getLogger(RecaptchaService.class);

    @Value("${recaptcha.secret-key}")
    private String secretKey;

    @Value("${recaptcha.verify-url}")
    private String verifyUrl;

    @Value("${app.recaptcha.enabled:true}")
    private boolean recaptchaEnabled;

    private static final double MIN_SCORE = 0.5;

    public boolean verifyRecaptcha(String token, String action) {
        // Если reCAPTCHA отключена в конфиге
        if (!recaptchaEnabled) {
            logger.info("reCAPTCHA is disabled in config, skipping verification");
            return true;
        }

        if (token == null || token.isBlank()) {
            logger.warn("reCAPTCHA token is null or empty");
            return false;
        }

        try {
            RestTemplate restTemplate = new RestTemplate();

            String url = verifyUrl + "?secret=" + secretKey + "&response=" + token;
            String response = restTemplate.postForObject(url, null, String.class);

            logger.debug("reCAPTCHA response: {}", response);

            Gson gson = new Gson();
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = gson.fromJson(response, Map.class);

            Boolean success = (Boolean) responseMap.get("success");
            Double score = (Double) responseMap.get("score");
            String responseAction = (String) responseMap.get("action");

            logger.info("reCAPTCHA verification - success: {}, score: {}, action: {}, expected action: {}",
                success, score, responseAction, action);

            if (success == null || !success) {
                logger.warn("reCAPTCHA verification failed - success is false");
                return false;
            }

            if (score == null || score < MIN_SCORE) {
                logger.warn("reCAPTCHA score too low: {} (minimum: {})", score, MIN_SCORE);
                return false;
            }

            if (!action.equals(responseAction)) {
                logger.warn("reCAPTCHA action mismatch - expected: {}, got: {}", action, responseAction);
                return false;
            }

            logger.info("reCAPTCHA verification successful for action: {}", action);
            return true;

        } catch (Exception e) {
            logger.error("reCAPTCHA verification error: {}", e.getMessage(), e);
            return false;
        }
    }
}

