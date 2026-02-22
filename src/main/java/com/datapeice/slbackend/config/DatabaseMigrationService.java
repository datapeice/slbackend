package com.datapeice.slbackend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Handles schema changes that Hibernate ddl-auto=update cannot do automatically
 * (e.g., changing column types from varchar(255) to TEXT).
 */
@Component
public class DatabaseMigrationService implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMigrationService.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Convert text columns that may have been created as VARCHAR(255)
        alterColumnToText("users", "avatar_url");
        alterColumnToText("users", "bio");
        alterColumnToText("users", "ban_reason");
        // Add new columns that may be missing in older deployments
        addColumnIfNotExists("users", "discord_verified", "BOOLEAN NOT NULL DEFAULT FALSE");
        addColumnIfNotExists("users", "discord_user_id", "VARCHAR(255)");
        createWarningsTableIfNotExists();
        createSiteSettingsTableIfNotExists();
    }

    /**
     * Alters a column to TEXT type unconditionally (if it exists and is not already TEXT).
     */
    private void alterColumnToText(String table, String column) {
        try {
            jdbcTemplate.execute(
                "DO $$ BEGIN " +
                "IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = '" + table + "' " +
                "AND column_name = '" + column + "' AND data_type != 'text') THEN " +
                "EXECUTE 'ALTER TABLE " + table + " ALTER COLUMN " + column + " TYPE TEXT'; " +
                "END IF; END $$;"
            );
            logger.info("Column migration checked (->TEXT): {}.{}", table, column);
        } catch (Exception e) {
            logger.warn("Could not migrate column {}.{} to TEXT: {}", table, column, e.getMessage());
        }
    }

    /**
     * Adds a column to a table if it doesn't already exist.
     */
    private void addColumnIfNotExists(String table, String column, String definition) {
        try {
            jdbcTemplate.execute(
                "DO $$ BEGIN " +
                "IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = '" + table + "' " +
                "AND column_name = '" + column + "') THEN " +
                "EXECUTE 'ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition + "'; " +
                "END IF; END $$;"
            );
            logger.info("Column existence checked: {}.{}", table, column);
        } catch (Exception e) {
            logger.warn("Could not add column {}.{}: {}", table, column, e.getMessage());
        }
    }

    private void createWarningsTableIfNotExists() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS warnings (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    reason TEXT NOT NULL,
                    issued_by_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
                    created_at TIMESTAMP DEFAULT NOW(),
                    active BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
            logger.info("warnings table ensured");
        } catch (Exception e) {
            logger.warn("Could not create warnings table: {}", e.getMessage());
        }
    }

    private void createSiteSettingsTableIfNotExists() {
        try {
            jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS site_settings (
                    id BIGINT PRIMARY KEY DEFAULT 1,
                    max_warnings_before_ban INTEGER NOT NULL DEFAULT 3,
                    auto_ban_on_max_warnings BOOLEAN NOT NULL DEFAULT TRUE,
                    send_email_on_warning BOOLEAN NOT NULL DEFAULT TRUE,
                    send_discord_dm_on_warning BOOLEAN NOT NULL DEFAULT TRUE,
                    send_email_on_ban BOOLEAN NOT NULL DEFAULT TRUE,
                    send_discord_dm_on_ban BOOLEAN NOT NULL DEFAULT TRUE,
                    send_email_on_application_approved BOOLEAN NOT NULL DEFAULT TRUE,
                    send_email_on_application_rejected BOOLEAN NOT NULL DEFAULT TRUE,
                    applications_open BOOLEAN NOT NULL DEFAULT TRUE,
                    registration_open BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
            logger.info("site_settings table ensured");
        } catch (Exception e) {
            logger.warn("Could not create site_settings table: {}", e.getMessage());
        }
    }
}

