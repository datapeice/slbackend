package com.datapeice.slbackend.service;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;

@Service
public class DiscordService {

    private static final Logger logger = LoggerFactory.getLogger(DiscordService.class);

    @Value("${discord.bot.token:}")
    private String botToken;

    @Value("${discord.guild.id:}")
    private String guildId;

    @Value("${discord.sl-role.id:}")
    private String slRoleId;

    @Value("${discord.bot.enabled:false}")
    private boolean botEnabled;

    private JDA jda;

    private final FileStorageService fileStorageService;

    public DiscordService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostConstruct
    public void init() {
        if (!botEnabled || botToken.isBlank()) {
            logger.info("Discord bot is disabled or token not configured");
            return;
        }
        try {
            jda = JDABuilder.createDefault(botToken)
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.DIRECT_MESSAGES
                    )
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .build();
            jda.awaitReady();
            logger.info("Discord bot started successfully. Guilds: {}", jda.getGuilds().size());
        } catch (Exception e) {
            logger.error("Failed to start Discord bot: {}", e.getMessage());
            jda = null;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    public boolean isEnabled() {
        return botEnabled && jda != null;
    }

    /**
     * Check if a member with the given Discord nickname is in the guild.
     * Searches by username (case-insensitive).
     */
    public boolean isMemberInGuild(String discordNickname) {
        if (!isEnabled()) {
            logger.warn("Discord bot disabled - skipping guild membership check for: {}", discordNickname);
            return true; // Allow if bot is disabled
        }
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            logger.error("Guild not found: {}", guildId);
            return false;
        }

        String searchName = discordNickname.toLowerCase().trim();
        // Remove discriminator if present (old Discord format user#1234)
        if (searchName.contains("#")) {
            searchName = searchName.substring(0, searchName.indexOf("#"));
        }
        final String finalSearch = searchName;

        try {
            // Force load ALL members from Discord API (not just cache)
            List<Member> members = guild.loadMembers().get();
            boolean found = members.stream().anyMatch(m ->
                    m.getUser().getName().toLowerCase().equals(finalSearch) ||
                    (m.getNickname() != null && m.getNickname().toLowerCase().equals(finalSearch)) ||
                    (m.getUser().getGlobalName() != null && m.getUser().getGlobalName().toLowerCase().equals(finalSearch))
            );
            logger.info("Discord guild membership check for '{}': {} (total members: {})", discordNickname, found, members.size());
            return found;
        } catch (Exception e) {
            logger.error("Failed to load guild members: {}", e.getMessage());
            // On error - allow the application to proceed
            return true;
        }
    }

    /**
     * Find Discord user ID by nickname (username).
     */
    public Optional<String> findDiscordUserId(String discordNickname) {
        if (!isEnabled()) return Optional.empty();
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return Optional.empty();

        String searchName = discordNickname.toLowerCase().trim();
        if (searchName.contains("#")) {
            searchName = searchName.substring(0, searchName.indexOf("#"));
        }
        final String finalSearch = searchName;

        try {
            List<Member> members = guild.loadMembers().get();
            return members.stream()
                    .filter(m ->
                            m.getUser().getName().toLowerCase().equals(finalSearch) ||
                            (m.getNickname() != null && m.getNickname().toLowerCase().equals(finalSearch)) ||
                            (m.getUser().getGlobalName() != null && m.getUser().getGlobalName().toLowerCase().equals(finalSearch))
                    )
                    .map(m -> m.getUser().getId())
                    .findFirst();
        } catch (Exception e) {
            logger.error("Failed to find Discord user ID for {}: {}", discordNickname, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Send a private DM to a Discord user by their user ID.
     */
    public void sendDirectMessage(String discordUserId, String message) {
        if (!isEnabled() || discordUserId == null || discordUserId.isBlank()) return;
        try {
            jda.retrieveUserById(discordUserId).queue(user -> {
                user.openPrivateChannel().queue(channel -> {
                    channel.sendMessage(message).queue(
                            success -> logger.info("DM sent to Discord user: {}", discordUserId),
                            error -> logger.error("Failed to send DM to {}: {}", discordUserId, error.getMessage())
                    );
                });
            }, error -> logger.error("Failed to find Discord user {}: {}", discordUserId, error.getMessage()));
        } catch (Exception e) {
            logger.error("Error sending DM to {}: {}", discordUserId, e.getMessage());
        }
    }

    /**
     * Assign the @SL role to a Discord user by their user ID.
     */
    public void assignSlRole(String discordUserId) {
        if (!isEnabled() || discordUserId == null || discordUserId.isBlank() || slRoleId.isBlank()) return;
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return;
        Role role = guild.getRoleById(slRoleId);
        if (role == null) {
            logger.error("SL role not found: {}", slRoleId);
            return;
        }
        guild.retrieveMemberById(discordUserId).queue(member -> {
            guild.addRoleToMember(member, role).queue(
                    success -> logger.info("SL role assigned to {}", discordUserId),
                    error -> logger.error("Failed to assign SL role to {}: {}", discordUserId, error.getMessage())
            );
        }, error -> logger.error("Member not found: {}", discordUserId));
    }

    /**
     * Remove the @SL role from a Discord user.
     */
    public void removeSlRole(String discordUserId) {
        if (!isEnabled() || discordUserId == null || discordUserId.isBlank() || slRoleId.isBlank()) return;
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return;
        Role role = guild.getRoleById(slRoleId);
        if (role == null) return;
        guild.retrieveMemberById(discordUserId).queue(member -> {
            guild.removeRoleFromMember(member, role).queue(
                    success -> logger.info("SL role removed from {}", discordUserId),
                    error -> logger.error("Failed to remove SL role from {}: {}", discordUserId, error.getMessage())
            );
        }, error -> logger.warn("Member not found for role removal: {}", discordUserId));
    }

    /**
     * Assign a specific Discord role to a user (for badge sync).
     */
    public void assignRole(String discordUserId, String roleId) {
        if (!isEnabled() || discordUserId == null || roleId == null || roleId.isBlank()) return;
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return;
        Role role = guild.getRoleById(roleId);
        if (role == null) {
            logger.error("Role not found: {}", roleId);
            return;
        }
        guild.retrieveMemberById(discordUserId).queue(member -> {
            guild.addRoleToMember(member, role).queue(
                    success -> logger.info("Role {} assigned to {}", roleId, discordUserId),
                    error -> logger.error("Failed to assign role {} to {}: {}", roleId, discordUserId, error.getMessage())
            );
        }, error -> logger.warn("Member not found: {}", discordUserId));
    }

    /**
     * Remove a specific Discord role from a user (for badge sync).
     */
    public void removeRole(String discordUserId, String roleId) {
        if (!isEnabled() || discordUserId == null || roleId == null || roleId.isBlank()) return;
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return;
        Role role = guild.getRoleById(roleId);
        if (role == null) return;
        guild.retrieveMemberById(discordUserId).queue(member -> {
            guild.removeRoleFromMember(member, role).queue(
                    success -> logger.info("Role {} removed from {}", roleId, discordUserId),
                    error -> logger.error("Failed to remove role {} from {}: {}", roleId, discordUserId, error.getMessage())
            );
        }, error -> logger.warn("Member not found: {}", discordUserId));
    }

    /**
     * Get list of guild members (for server membership check).
     */
    public int getGuildMemberCount() {
        if (!isEnabled()) return 0;
        Guild guild = jda.getGuildById(guildId);
        return guild != null ? guild.getMemberCount() : 0;
    }

    /**
     * Download Discord avatar by discordUserId and upload to MinIO.
     * Returns the new MinIO URL, or null if failed.
     */
    public String syncDiscordAvatar(String discordUserId) {
        if (!isEnabled() || discordUserId == null || discordUserId.isBlank()) return null;
        try {
            // Get avatar URL from Discord
            User discordUser = jda.retrieveUserById(discordUserId).complete();
            if (discordUser == null) return null;

            String avatarUrl = discordUser.getAvatarUrl();
            if (avatarUrl == null) {
                // Use default Discord avatar
                avatarUrl = discordUser.getDefaultAvatarUrl();
            }
            // Force PNG format, size 256
            if (avatarUrl.contains("?")) {
                avatarUrl = avatarUrl.split("\\?")[0] + "?size=256";
            } else {
                avatarUrl = avatarUrl + "?size=256";
            }

            // Download avatar bytes
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(avatarUrl))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                logger.warn("Failed to download Discord avatar for {}: HTTP {}", discordUserId, response.statusCode());
                return null;
            }

            String contentType = response.headers().firstValue("content-type").orElse("image/png");
            String extension = contentType.contains("gif") ? ".gif" : ".png";
            long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1);

            String minioUrl = fileStorageService.uploadFromStream(
                    response.body(), contentLength, contentType, "avatars", extension
            );

            logger.info("Discord avatar synced to MinIO for discordUserId={}: {}", discordUserId, minioUrl);
            return minioUrl;

        } catch (Exception e) {
            logger.error("Failed to sync Discord avatar for {}: {}", discordUserId, e.getMessage());
            return null;
        }
    }
}

