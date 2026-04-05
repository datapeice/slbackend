package com.datapeice.slbackend.repository;

import com.datapeice.slbackend.entity.Badge;
import com.datapeice.slbackend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailVerificationToken(String token);

    Optional<User> findByResetPasswordToken(String token);

    Optional<User> findByDiscordOauthState(String discordOauthState);

    List<User> findByEmailVerifiedTrue();

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByDiscordNickname(String discordNickname);

    boolean existsByMinecraftNickname(String minecraftNickname);

    Optional<User> findByDiscordUserId(String discordUserId);

    Page<User> findAll(Pageable pageable);

    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE " +
            "(LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.discordNickname) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.minecraftNickname) LIKE LOWER(CONCAT('%', :query, '%'))) " +
            "AND (:role IS NULL OR u.role = :role)")
    org.springframework.data.domain.Page<User> findBySearch(
            @org.springframework.data.repository.query.Param("query") String query,
            @org.springframework.data.repository.query.Param("role") com.datapeice.slbackend.entity.UserRole role,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE User u SET u.inSeason = false")
    void resetSeasonForAll();
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(u) FROM User u WHERE u.isPlayer = true")
    long countActivePlayers();

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(u) FROM User u WHERE u.banned = true")
    long countBannedUsers();
}
