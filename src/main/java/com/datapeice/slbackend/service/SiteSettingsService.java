package com.datapeice.slbackend.service;

import com.datapeice.slbackend.dto.SiteSettingsRequest;
import com.datapeice.slbackend.entity.SiteSettings;
import com.datapeice.slbackend.repository.SiteSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteSettingsService {

    private final SiteSettingsRepository siteSettingsRepository;
    private final AuditLogService auditLogService;

    public SiteSettingsService(SiteSettingsRepository siteSettingsRepository, AuditLogService auditLogService) {
        this.siteSettingsRepository = siteSettingsRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public SiteSettings getSettings() {
        return siteSettingsRepository.findById(1L).orElseGet(() -> {
            SiteSettings defaults = new SiteSettings();
            return siteSettingsRepository.save(defaults);
        });
    }

    @Transactional
    public SiteSettings updateSettings(SiteSettingsRequest request, Long adminId, String adminName) {
        SiteSettings settings = getSettings();
        java.util.List<String> changes = new java.util.ArrayList<>();

        if (request.getMaxWarningsBeforeBan() != null
                && !request.getMaxWarningsBeforeBan().equals(settings.getMaxWarningsBeforeBan())) {
            changes.add(
                    "Max Warnings: " + settings.getMaxWarningsBeforeBan() + " -> " + request.getMaxWarningsBeforeBan());
            settings.setMaxWarningsBeforeBan(request.getMaxWarningsBeforeBan());
        }
        if (request.getAutoBanOnMaxWarnings() != null
                && !request.getAutoBanOnMaxWarnings().equals(settings.isAutoBanOnMaxWarnings())) {
            changes.add("Auto Ban: " + settings.isAutoBanOnMaxWarnings() + " -> " + request.getAutoBanOnMaxWarnings());
            settings.setAutoBanOnMaxWarnings(request.getAutoBanOnMaxWarnings());
        }
        if (request.getSendEmailOnWarning() != null
                && !request.getSendEmailOnWarning().equals(settings.isSendEmailOnWarning())) {
            changes.add(
                    "Email on Warning: " + settings.isSendEmailOnWarning() + " -> " + request.getSendEmailOnWarning());
            settings.setSendEmailOnWarning(request.getSendEmailOnWarning());
        }
        if (request.getSendDiscordDmOnWarning() != null
                && !request.getSendDiscordDmOnWarning().equals(settings.isSendDiscordDmOnWarning())) {
            changes.add("Discord DM on Warning: " + settings.isSendDiscordDmOnWarning() + " -> "
                    + request.getSendDiscordDmOnWarning());
            settings.setSendDiscordDmOnWarning(request.getSendDiscordDmOnWarning());
        }
        if (request.getSendEmailOnBan() != null && !request.getSendEmailOnBan().equals(settings.isSendEmailOnBan())) {
            changes.add("Email on Ban: " + settings.isSendEmailOnBan() + " -> " + request.getSendEmailOnBan());
            settings.setSendEmailOnBan(request.getSendEmailOnBan());
        }
        if (request.getSendDiscordDmOnBan() != null
                && !request.getSendDiscordDmOnBan().equals(settings.isSendDiscordDmOnBan())) {
            changes.add(
                    "Discord DM on Ban: " + settings.isSendDiscordDmOnBan() + " -> " + request.getSendDiscordDmOnBan());
            settings.setSendDiscordDmOnBan(request.getSendDiscordDmOnBan());
        }
        if (request.getSendEmailOnApplicationApproved() != null
                && !request.getSendEmailOnApplicationApproved().equals(settings.isSendEmailOnApplicationApproved())) {
            changes.add("Email (App Approved): " + settings.isSendEmailOnApplicationApproved() + " -> "
                    + request.getSendEmailOnApplicationApproved());
            settings.setSendEmailOnApplicationApproved(request.getSendEmailOnApplicationApproved());
        }
        if (request.getSendEmailOnApplicationRejected() != null
                && !request.getSendEmailOnApplicationRejected().equals(settings.isSendEmailOnApplicationRejected())) {
            changes.add("Email (App Rejected): " + settings.isSendEmailOnApplicationRejected() + " -> "
                    + request.getSendEmailOnApplicationRejected());
            settings.setSendEmailOnApplicationRejected(request.getSendEmailOnApplicationRejected());
        }
        if (request.getApplicationsOpen() != null
                && !request.getApplicationsOpen().equals(settings.isApplicationsOpen())) {
            changes.add("Apps Open: " + settings.isApplicationsOpen() + " -> " + request.getApplicationsOpen());
            settings.setApplicationsOpen(request.getApplicationsOpen());
        }
        if (request.getRegistrationOpen() != null
                && !request.getRegistrationOpen().equals(settings.isRegistrationOpen())) {
            changes.add("Reg Open: " + settings.isRegistrationOpen() + " -> " + request.getRegistrationOpen());
            settings.setRegistrationOpen(request.getRegistrationOpen());
        }

        SiteSettings saved = siteSettingsRepository.save(settings);

        if (!changes.isEmpty()) {
            auditLogService.logAction(adminId, adminName, "ADMIN_UPDATE_SETTINGS",
                    "Изменил настройки: " + String.join(", ", changes), null, null);
        }

        return saved;
    }
}
