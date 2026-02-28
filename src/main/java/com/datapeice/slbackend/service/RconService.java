package com.datapeice.slbackend.service;

import lombok.extern.slf4j.Slf4j;
import nl.vv32.rcon.Rcon;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class RconService {

    @Value("${minecraft.rcon.enabled}")
    private boolean enabled;

    @Value("${minecraft.rcon.ip}")
    private String ip;

    @Value("${minecraft.rcon.port}")
    private int port;

    @Value("${minecraft.rcon.password}")
    private String password;

    public boolean sendCommand(String command) {
        if (!enabled) {
            log.info("RCON disabled. Skipping command: {}", command);
            return false;
        }

        log.info("Sending RCON command: {}", command);
        try (Rcon rcon = Rcon.open(ip, port)) {
            if (rcon.authenticate(password)) {
                String result = rcon.sendCommand(command);
                log.info("RCON command result: {}", result);
                return true;
            } else {
                log.error("Failed to authenticate to RCON server at {}:{}", ip, port);
            }
        } catch (IOException e) {
            log.error("Error connecting to RCON server (IP: {}, Port: {}): ", ip, port, e);
        } catch (Exception e) {
            log.error("Unexpected error executing RCON command", e);
        }
        return false;
    }

    public void addPlayerToWhitelist(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            return;
        }
        sendCommand("easywhitelist add " + nickname);
    }

    public void removePlayerFromWhitelist(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            return;
        }
        sendCommand("easywhitelist remove " + nickname);
    }
}
