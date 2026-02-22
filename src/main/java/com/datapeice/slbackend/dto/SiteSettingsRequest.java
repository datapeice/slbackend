package com.datapeice.slbackend.dto;

import lombok.Data;

@Data
public class SiteSettingsRequest {
    private Integer maxWarningsBeforeBan;
    private Boolean autoBanOnMaxWarnings;
    private Boolean sendEmailOnWarning;
    private Boolean sendDiscordDmOnWarning;
    private Boolean sendEmailOnBan;
    private Boolean sendDiscordDmOnBan;
    private Boolean sendEmailOnApplicationApproved;
    private Boolean sendEmailOnApplicationRejected;
    private Boolean applicationsOpen;
    private Boolean registrationOpen;
}

