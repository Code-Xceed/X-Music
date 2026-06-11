package com.codexceed.xmusic.i18n;

import com.codexceed.xmusic.XMusic;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple i18n system for CodeX Music Player.
 * Loads language files from config/xmusic/lang/<locale>.json.
 * Falls back to English if a key is missing.
 * Default English strings are embedded in the code.
 */
public final class I18n {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    /** Current locale code (e.g. "en", "es", "fr", "de", "ja", "zh"). */
    private static String locale = "en";

    /** Active translations for the current locale. */
    private static Map<String, String> translations = new HashMap<>();

    /** English fallback â€” always populated from defaults. */
    private static final Map<String, String> fallback = new HashMap<>();

    private static Path langDir;

    static {
        // Built-in English defaults
        fallback.put("xmusic.home.title", "Home");
        fallback.put("xmusic.home.greeting", "Welcome back");
        fallback.put("xmusic.home.quick_play", "Quick Play");
        fallback.put("xmusic.home.see_all", "See all");
        fallback.put("xmusic.home.recently_played", "Recently Played");
        fallback.put("xmusic.home.most_replayed", "Most Replayed");
        fallback.put("xmusic.home.favorites", "Favorites");

        fallback.put("xmusic.search.title", "Search");
        fallback.put("xmusic.search.placeholder", "Search YouTube, Spotify, SoundCloud...");
        fallback.put("xmusic.search.enter_url", "Enter URL to load track/playlist");
        fallback.put("xmusic.search.history", "Recent Searches");
        fallback.put("xmusic.search.results", "Results");
        fallback.put("xmusic.search.no_results", "No results found");

        fallback.put("xmusic.library.title", "Library");
        fallback.put("xmusic.library.favorites", "Favorites");
        fallback.put("xmusic.library.playlists", "Playlists");
        fallback.put("xmusic.library.artists", "Artists");
        fallback.put("xmusic.library.albums", "Albums");
        fallback.put("xmusic.library.sources", "Sources");
        fallback.put("xmusic.library.local", "Local");
        fallback.put("xmusic.library.create_playlist", "Create Playlist");
        fallback.put("xmusic.library.playlist_name", "Playlist Name");
        fallback.put("xmusic.library.add_to_playlist", "Add to Playlist");

        fallback.put("xmusic.downloads.title", "Downloads");
        fallback.put("xmusic.downloads.search", "Search to download");
        fallback.put("xmusic.downloads.downloading", "Downloading");
        fallback.put("xmusic.downloads.complete", "Complete");
        fallback.put("xmusic.downloads.failed", "Failed");
        fallback.put("xmusic.downloads.queued", "Queued");
        fallback.put("xmusic.downloads.tool_status", "Tool Status");

        fallback.put("xmusic.settings.title", "Settings");
        fallback.put("xmusic.settings.playback", "Playback");
        fallback.put("xmusic.settings.hud", "HUD");
        fallback.put("xmusic.settings.youtube", "YouTube");
        fallback.put("xmusic.settings.storage", "Storage");
        fallback.put("xmusic.settings.about", "About");
        fallback.put("xmusic.settings.auto_resume", "Auto-Resume on Restart");
        fallback.put("xmusic.settings.hud_enabled", "Show HUD Overlay");
        fallback.put("xmusic.settings.hud_position", "HUD Position");
        fallback.put("xmusic.settings.hud_auto_hide", "Auto-hide (seconds, 0=always)");
        fallback.put("xmusic.settings.volume_step", "Volume Step");

        fallback.put("xmusic.player.play", "Play");
        fallback.put("xmusic.player.pause", "Pause");
        fallback.put("xmusic.player.next", "Next Track");
        fallback.put("xmusic.player.previous", "Previous Track");
        fallback.put("xmusic.player.rewind", "Rewind 5s");
        fallback.put("xmusic.player.forward", "Forward 5s");
        fallback.put("xmusic.player.shuffle", "Shuffle");
        fallback.put("xmusic.player.loop", "Loop");
        fallback.put("xmusic.player.autoplay", "Autoplay");
        fallback.put("xmusic.player.volume", "Volume");
        fallback.put("xmusic.player.mute", "Mute");
        fallback.put("xmusic.player.nothing_playing", "Nothing playing");
        fallback.put("xmusic.player.backend_idle", "Backend idle");

        fallback.put("xmusic.source.local", "Local");
        fallback.put("xmusic.source.youtube", "YouTube");
        fallback.put("xmusic.source.spotify", "Spotify");
        fallback.put("xmusic.source.soundcloud", "SoundCloud");
        fallback.put("xmusic.source.bandcamp", "Bandcamp");
        fallback.put("xmusic.source.vimeo", "Vimeo");
        fallback.put("xmusic.source.twitch", "Twitch");
        fallback.put("xmusic.source.stream", "Stream");
        fallback.put("xmusic.source.downloaded", "Downloaded");

        fallback.put("xmusic.hud.playing", "Playing");
        fallback.put("xmusic.hud.paused", "Paused");
        fallback.put("xmusic.hud.stopped", "Stopped");

        fallback.put("xmusic.key.open_player", "Open Player");
        fallback.put("xmusic.key.play_pause", "Play/Pause");
        fallback.put("xmusic.key.next_track", "Next Track");
        fallback.put("xmusic.key.prev_track", "Previous Track");
        fallback.put("xmusic.key.volume_up", "Volume Up");
        fallback.put("xmusic.key.volume_down", "Volume Down");
        fallback.put("xmusic.key.toggle_shuffle", "Toggle Shuffle");
        fallback.put("xmusic.key.cycle_loop", "Cycle Loop");
        fallback.put("xmusic.key.cycle_playback_mode", "Cycle Playback Mode");

        fallback.put("xmusic.action.favorite", "Favorite");
        fallback.put("xmusic.action.unfavorite", "Unfavorite");
        fallback.put("xmusic.action.download", "Download");
        fallback.put("xmusic.action.add_to_queue", "Add to Queue");
        fallback.put("xmusic.action.remove", "Remove");
    }

    private I18n() {}

    /** Initialize the i18n system with the config directory. */
    public static void init(Path configDir) {
        langDir = configDir.resolve("xmusic").resolve("lang");
        try {
            Files.createDirectories(langDir);
        } catch (Exception e) {
            XMusic.LOGGER.error("Failed to create lang directory", e);
        }
        // Generate default English file if missing
        generateDefaultLangFile("en", fallback);
        // Load current locale
        loadLocale(locale);
        XMusic.LOGGER.info("[I18n] Initialized with locale: {}", locale);
    }

    /** Set the active locale and reload translations. */
    public static void setLocale(String newLocale) {
        locale = newLocale;
        loadLocale(locale);
        XMusic.LOGGER.info("[I18n] Locale changed to: {}", locale);
    }

    /** Get the current locale code. */
    public static String getLocale() {
        return locale;
    }

    /**
     * Translate a key. Falls back to English, then to the key itself.
     * Supports simple positional args: t("xmusic.greeting", "Player")
     * replaces {0} with "Player".
     */
    public static String t(String key, Object... args) {
        String value = translations.get(key);
        if (value == null) {
            value = fallback.get(key);
        }
        if (value == null) {
            value = key;
        }
        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                value = value.replace("{" + i + "}", String.valueOf(args[i]));
            }
        }
        return value;
    }

    /** Load translations for a locale from disk. */
    private static void loadLocale(String loc) {
        translations.clear();
        if ("en".equals(loc)) {
            translations.putAll(fallback);
            return;
        }
        Path file = langDir.resolve(loc + ".json");
        if (Files.exists(file)) {
            try (Reader reader = new InputStreamReader(new FileInputStream(file.toFile()), StandardCharsets.UTF_8)) {
                Map<String, String> loaded = GSON.fromJson(reader, MAP_TYPE);
                if (loaded != null) {
                    translations.putAll(loaded);
                }
            } catch (Exception e) {
                XMusic.LOGGER.error("[I18n] Failed to load locale: {}", loc, e);
            }
        }
        // Always fill missing keys from English fallback
        for (Map.Entry<String, String> entry : fallback.entrySet()) {
            translations.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    /** Generate a default language file so users can translate it. */
    private static void generateDefaultLangFile(String loc, Map<String, String> defaults) {
        Path file = langDir.resolve(loc + ".json");
        if (!Files.exists(file)) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(defaults, writer);
            } catch (Exception e) {
                XMusic.LOGGER.debug("[I18n] Could not generate default lang file", e);
            }
        }
    }

    /** Get all available locale codes (from lang/*.json files). */
    public static java.util.List<String> getAvailableLocales() {
        java.util.List<String> locales = new java.util.ArrayList<>();
        locales.add("en");
        if (langDir != null && Files.exists(langDir)) {
            try (java.util.stream.Stream<Path> files = Files.list(langDir)) {
                files.filter(p -> p.toString().endsWith(".json"))
                     .map(p -> p.getFileName().toString().replace(".json", ""))
                     .filter(l -> !"en".equals(l))
                     .forEach(locales::add);
            } catch (Exception ignored) {}
        }
        return locales;
    }
}
