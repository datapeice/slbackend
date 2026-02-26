package com.datapeice.slbackend.repository;

import com.datapeice.slbackend.entity.Badge;
import com.datapeice.slbackend.entity.User;
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

    List<User> findAllByBadgesContaining(Badge badge);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE User u SET u.inSeason = false")
    void resetSeasonForAll();
}
