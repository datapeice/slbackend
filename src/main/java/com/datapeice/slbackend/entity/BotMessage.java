package com.datapeice.slbackend.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "bot_messages")
@Data
public class BotMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipientUser;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_admin_id")
    private User senderAdmin;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(columnDefinition = "TEXT")
    private String mediaUrl;

    private String discordMessageId;

    private boolean isEdited = false;

    private boolean isFromPlayer = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
