package com.datapeice.slbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AnticheatSnapshotResponse {

    private Long id;
    private String playerName;
    private String playerUuid;
    private String launcherName;
    private String launcherBrand;
    private List<String> mods;
    private List<String> resourcePacks;
    private List<ProcessInfo> processes;
    private LocalDateTime createdAt;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProcessInfo {
        private String imageName;
        private String pid;
        private String memUsage;
        private String status;
        private String windowTitle;
    }
}
