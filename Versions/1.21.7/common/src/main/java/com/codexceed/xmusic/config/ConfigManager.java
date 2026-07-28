package com.codexceed.xmusic.config;

import com.codexceed.xmusic.XMusic;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manages loading and saving of {@link XMusicConfig}.
 * Config is stored as prettified JSON in {@code config/xmusic.json}.
 */
public final class ConfigManager {
    private static final String CONFIG_FILE_NAME = "xmusic.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static XMusicConfig config;
    private static Path configPath;

    private ConfigManager() {}

    /**
     * Initialize the config manager with the given config directory.
     * Loads existing config or creates defaults.
     */
    public static void init(Path configDir) {
        configPath = configDir.resolve(CONFIG_FILE_NAME);
        load();
    }

    /**
     * Get the current config instance.
     */
    public static XMusicConfig get() {
        if (config == null) {
            config = new XMusicConfig();
        }
        return config;
    }

    /**
     * Load config from disk. If the file doesn't exist, create it with defaults.
     */
    public static void load() {
        if (configPath == null) {
            XMusic.LOGGER.warn("Config path not set, using defaults.");
            config = new XMusicConfig();
            return;
        }

        File file = configPath.toFile();
        if (file.exists()) {
            try (Reader reader = new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, XMusicConfig.class);
                if (config == null) {
                    config = new XMusicConfig();
                }
                config.validate();
                XMusic.LOGGER.info("Configuration loaded successfully.");
            } catch (Exception e) {
                XMusic.LOGGER.error("Failed to load config, using defaults.", e);
                config = new XMusicConfig();
            }
        } else {
            XMusic.LOGGER.info("No config found, creating defaults...");
            config = new XMusicConfig();
            save();
        }
    }

    /**
     * Save the current config to disk atomically.
     */
    public static void save() {
        if (configPath == null) {
            XMusic.LOGGER.warn("Config path not set, cannot save.");
            return;
        }

        try {
            // Ensure parent directories exist
            Files.createDirectories(configPath.getParent());

            Path tempPath = configPath.resolveSibling(CONFIG_FILE_NAME + ".tmp");
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(tempPath.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
            Files.move(tempPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            XMusic.LOGGER.debug("Configuration saved.");
        } catch (Exception e) {
            XMusic.LOGGER.error("Failed to save config!", e);
        }
    }

    /**
     * Reset config to defaults and save.
     */
    public static void reset() {
        config = new XMusicConfig();
        save();
    }
}
