package com.codexceed.xmusic;

import com.codexceed.xmusic.audio.AudioPlayer;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.config.XMusicConfig;
import com.codexceed.xmusic.platform.PlatformHelper;
import com.codexceed.xmusic.service.ServiceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CodeX Music Player - Main entry point.
 * All shared initialization goes here. Loader-specific modules
 * call {@link #init(PlatformHelper)} and {@link #initClient()}.
 */
public final class XMusic {
    public static final String MOD_ID = "xmusic";
    public static final String MOD_NAME = "CodeX Music Player";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private static PlatformHelper platform;
    private static boolean initialized = false;
    private static boolean clientInitialized = false;

    private XMusic() {}

    /**
     * Common initialization called from the Fabric client entry point.
     * Sets up config, logging, and platform bindings.
     */
    public static void init(PlatformHelper platformHelper) {
        if (initialized) return;
        initialized = true;

        platform = platformHelper;
        LOGGER.info("===========================================");
        LOGGER.info("  CodeX Music Player v{} initializing...", getVersion());
        LOGGER.info("  Platform: {}", platform.getPlatformName());
        LOGGER.info("===========================================");

        ConfigManager.init(platform.getConfigDir());
        com.codexceed.xmusic.i18n.I18n.init(platform.getConfigDir());
        com.codexceed.xmusic.i18n.I18n.setLocale(ConfigManager.get().locale);
        com.codexceed.xmusic.library.LibraryManager.getInstance().init(platform.getConfigDir());
        com.codexceed.xmusic.download.DownloadManager.getInstance().init(platform.getGameDir());
        LOGGER.info("Config loaded from {}", platform.getConfigDir());
    }

    /**
     * Client-side initialization for audio, services, and UI hooks.
     * Must be called after {@link #init(PlatformHelper)}.
     */
    public static void initClient() {
        if (clientInitialized) return;
        clientInitialized = true;

        LOGGER.info("Initializing client systems...");

        AudioPlayer.getInstance().init();
        LOGGER.info("Audio engine ready.");

        ServiceManager.init();
        LOGGER.info("Services initialized.");


        LOGGER.info("CodeX Music Player backend is ready.");
    }

    public static PlatformHelper getPlatform() {
        return platform;
    }

    public static XMusicConfig getConfig() {
        return ConfigManager.get();
    }

    public static String getVersion() {
        return "1.0.0";
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
