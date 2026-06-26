package com.example.pstarchive.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AppPaths {
    private static final String APP_DIR_NAME = "PstArchiveSearch";

    private AppPaths() {
    }

    public static Path defaultDataDir() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, APP_DIR_NAME);
        }
        return Path.of(System.getProperty("user.home"), APP_DIR_NAME);
    }

    public static Path catalogDatabase(Path dataDir) {
        return dataDir.resolve("catalog").resolve("archive_catalog.sqlite");
    }

    public static Path shardsDir(Path dataDir) {
        return dataDir.resolve("shards");
    }

    public static Path logsDir(Path dataDir) {
        return dataDir.resolve("logs");
    }

    public static void ensureBaseDirectories(Path dataDir) throws IOException {
        Files.createDirectories(dataDir.resolve("catalog"));
        Files.createDirectories(shardsDir(dataDir));
        Files.createDirectories(logsDir(dataDir));
    }
}
