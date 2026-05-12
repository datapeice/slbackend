package com.datapeice.slbackend.service;

import com.datapeice.slbackend.dto.AnticheatPayloadRequest;
import com.datapeice.slbackend.dto.AnticheatSnapshotResponse;
import com.datapeice.slbackend.entity.AnticheatSnapshot;
import com.datapeice.slbackend.repository.AnticheatSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AnticheatService {

    private final AnticheatSnapshotRepository snapshotRepository;
    private final RconService rconService;
    private final ObjectMapper objectMapper;

    @Value("${anticheat.retention-days:14}")
    private int retentionDays;

    public AnticheatService(AnticheatSnapshotRepository snapshotRepository,
                            RconService rconService,
                            ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.rconService = rconService;
        this.objectMapper = objectMapper;
    }

    /**
     * Save a new anticheat snapshot from Minecraft server telemetry.
     */
    public AnticheatSnapshot saveSnapshot(AnticheatPayloadRequest request) {
        AnticheatSnapshot snapshot = new AnticheatSnapshot();
        snapshot.setPlayerName(request.getPlayer());
        snapshot.setPlayerUuid(request.getUuid());
        snapshot.setCreatedAt(LocalDateTime.now());

        if (request.getData() != null) {
            snapshot.setLauncherName(request.getData().getLauncherName());
            snapshot.setLauncherBrand(request.getData().getLauncherBrand());
            snapshot.setProcesses(request.getData().getProcesses());

            // Store mods and resourcepacks as JSON arrays
            try {
                if (request.getData().getMods() != null) {
                    snapshot.setMods(objectMapper.writeValueAsString(request.getData().getMods()));
                }
                if (request.getData().getResourcepacks() != null) {
                    snapshot.setResourcePacks(objectMapper.writeValueAsString(request.getData().getResourcepacks()));
                }
            } catch (JsonProcessingException e) {
                log.error("[Anticheat] Failed to serialize mods/resourcepacks", e);
            }
        }

        AnticheatSnapshot saved = snapshotRepository.save(snapshot);
        log.info("[Anticheat] Saved snapshot #{} for player {}", saved.getId(), saved.getPlayerName());
        return saved;
    }

    /**
     * Get snapshots for a specific player.
     */
    public Page<AnticheatSnapshotResponse> getSnapshotsByPlayer(String playerName, Pageable pageable) {
        return snapshotRepository
                .findByPlayerNameIgnoreCaseOrderByCreatedAtDesc(playerName, pageable)
                .map(this::toResponse);
    }

    /**
     * Get all snapshots with optional search.
     */
    public Page<AnticheatSnapshotResponse> getAllSnapshots(String query, Pageable pageable) {
        if (query != null && !query.isBlank()) {
            return snapshotRepository.searchByPlayerName(query.trim(), pageable).map(this::toResponse);
        }
        return snapshotRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    /**
     * Get a single snapshot by ID.
     */
    public AnticheatSnapshotResponse getSnapshotById(Long id) {
        AnticheatSnapshot snapshot = snapshotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Snapshot not found: " + id));
        return toResponse(snapshot);
    }

    /**
     * Request an anticheat snapshot from a player via RCON.
     */
    public boolean requestSnapshot(String playerName) {
        log.info("[Anticheat] Requesting snapshot for player {} via RCON", playerName);
        return rconService.sendCommand("camera anticheat " + playerName);
    }

    /**
     * Cleanup snapshots older than retention period.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupOldSnapshots() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        snapshotRepository.deleteByCreatedAtBefore(cutoff);
        log.info("[Anticheat] Cleaned up snapshots older than {} days", retentionDays);
    }

    /**
     * Convert entity to response DTO with parsed processes.
     */
    private AnticheatSnapshotResponse toResponse(AnticheatSnapshot snapshot) {
        AnticheatSnapshotResponse response = new AnticheatSnapshotResponse();
        response.setId(snapshot.getId());
        response.setPlayerName(snapshot.getPlayerName());
        response.setPlayerUuid(snapshot.getPlayerUuid());
        response.setLauncherName(snapshot.getLauncherName());
        response.setLauncherBrand(snapshot.getLauncherBrand());
        response.setCreatedAt(snapshot.getCreatedAt());

        // Parse mods from JSON
        response.setMods(parseJsonList(snapshot.getMods()));

        // Parse resource packs from JSON
        response.setResourcePacks(parseJsonList(snapshot.getResourcePacks()));

        // Parse processes from CSV-like string
        response.setProcesses(parseProcesses(snapshot.getProcesses()));

        return response;
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("[Anticheat] Failed to parse JSON list: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Parse the process list from the CSV-like format:
     * "Image Name","PID","Session Name","Session#","Mem Usage","Status","User Name","CPU Time","Window Title"
     * separated by " | "
     */
    private List<AnticheatSnapshotResponse.ProcessInfo> parseProcesses(String rawProcesses) {
        List<AnticheatSnapshotResponse.ProcessInfo> result = new ArrayList<>();
        if (rawProcesses == null || rawProcesses.isBlank()) {
            return result;
        }

        String[] rows = rawProcesses.split("\\s*\\|\\s*");

        // Skip the header row (first element)
        boolean isFirst = true;
        for (String row : rows) {
            if (row.isBlank()) continue;

            // Remove surrounding quotes and split by ","
            String cleaned = row.trim();
            // Remove leading/trailing quotes if present
            if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }

            String[] fields = cleaned.split("\",\"");
            // Clean up remaining quotes
            for (int i = 0; i < fields.length; i++) {
                fields[i] = fields[i].replace("\"", "").trim();
            }

            if (isFirst) {
                isFirst = false;
                // Check if this is a header row
                if (fields.length > 0 && fields[0].equalsIgnoreCase("Image Name")) {
                    continue;
                }
            }

            if (fields.length >= 6) {
                AnticheatSnapshotResponse.ProcessInfo info = new AnticheatSnapshotResponse.ProcessInfo();
                info.setImageName(fields[0]);
                info.setPid(fields[1]);
                info.setMemUsage(fields.length > 4 ? fields[4] : "");
                info.setStatus(fields.length > 5 ? fields[5] : "");
                info.setWindowTitle(fields.length > 8 ? fields[8] : "N/A");
                result.add(info);
            }
        }

        return result;
    }
}
