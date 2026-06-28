package com.datapeice.slbackend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class BotMessageResponse {
    private Long id;
    private Long recipientUserId;
    private String recipientUsername;
    private String senderAdminName;
    private String content;
    private String mediaUrl;
    private boolean isEdited;
    private boolean isFromPlayer;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
