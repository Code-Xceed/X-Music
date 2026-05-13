package com.codexceed.xmusic.platform;

import java.nio.file.Path;

/**
 * Abstraction layer for loader-specific operations.
 * Implemented by FabricPlatformHelper and ForgePlatformHelper.
 */
public interface PlatformHelper {

    /**
     * @return The platform name, e.g. "Fabric" or "Forge".
     */
    String getPlatformName();

    /**
     * @return The config directory (e.g. .minecraft/config).
     */
    Path getConfigDir();

    /**
     * @return The game directory (e.g. .minecraft).
     */
    Path getGameDir();

    /**
     * Opens a URL in the system's default browser.
     */
    void openUrl(String url);

    /**
     * Opens a folder in the system file explorer.
     * @param path the directory path to open
     */
    default void openFolder(java.nio.file.Path path) {
        if (path == null || !java.nio.file.Files.isDirectory(path)) return;
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer", path.toAbsolutePath().toString());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", path.toAbsolutePath().toString());
            } else {
                pb = new ProcessBuilder("xdg-open", path.toAbsolutePath().toString());
            }
            pb.directory(path.toFile()).start();
        } catch (Exception e) {
            // Fallback to Desktop API
            try { java.awt.Desktop.getDesktop().open(path.toFile()); } catch (Exception ignored) {}
        }
    }

    /**
     * @return true if running on the client (not a dedicated server).
     */
    boolean isClient();

    /**
     * @return true if a mod with the given ID is loaded.
     */
    boolean isModLoaded(String modId);
}
