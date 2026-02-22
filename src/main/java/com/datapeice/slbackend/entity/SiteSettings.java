package com.datapeice.slbackend.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "site_settings")
@Data
public class SiteSettings {
    @Id
    private Long id = 1L; // singleton row

    // Warning settings
    private int maxWarningsBeforeBan = 3;
    private boolean autoBanOnMaxWarnings = true;
    private boolean sendEmailOnWarning = true;
    private boolean sendDiscordDmOnWarning = true;

    // Ban settings
    private boolean sendEmailOnBan = true;
    private boolean sendDiscordDmOnBan = true;

    // Application settings
    private boolean sendEmailOnApplicationApproved = true;
    private boolean sendEmailOnApplicationRejected = true;
    private boolean applicationsOpen = true;

    // Registration settings
    private boolean registrationOpen = true;
}

