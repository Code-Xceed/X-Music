package com.codexceed.xmusic.config;

/**
 * User configuration for the current production baseline.
 *
 * <p>Keep this focused on active behavior. Experimental integrations should
 * add their fields only when their source, UI, and verification path exist.
 */
public class XMusicConfig {
    /** Optional developer fallback API key for YouTube metadata. Normal users should not need this. */
    public String youtubeApiKey = "";

    /** Optional comma-separated Piped API instances for native YouTube audio resolution testing. */
    public String pipedApiInstances = "";

    /** Use yt-dlp + ffmpeg download/cache playback for YouTube instead of direct stream resolution. */
    public boolean youtubeUseDownloadPipeline = true;

    /** Optional explicit path to yt-dlp or yt-dlp.exe. Empty = auto-discover from xmusic/bin or PATH. */
    public String youtubeYtDlpPath = "";

    /** Optional explicit path to ffmpeg or ffmpeg.exe. Empty = auto-discover from xmusic/bin or PATH. */
    public String youtubeFfmpegPath = "";

    /** Override URL for yt-dlp auto-install. Empty = built-in default. */
    public String youtubeYtDlpDownloadUrl = "";

    /** Override URL for ffmpeg auto-install zip. Empty = built-in default. */
    public String youtubeFfmpegDownloadUrl = "";

    /** Directory for cached YouTube downloads. Empty = .minecraft/xmusic/cache/youtube. */
    public String youtubeCacheDirectory = "";

    /** Optional path to a cookies.txt file for authenticated yt-dlp requests. */
    public String youtubeCookiesFile = "";

    /** Directory for user-kept YouTube favorites. Empty = .minecraft/xmusic/library/youtube. */
    public String youtubeFavoritesDirectory = "";

    /** Maximum time to allow a YouTube download/conversion job before failing. */
    public int youtubeDownloadTimeoutSeconds = 180;

    /** Number of downloader fragments to use when yt-dlp supports segmented downloads. */
    public int youtubeDownloadConcurrentFragments = 4;

    /** Maximum size of the temporary YouTube cache in megabytes. */
    public int youtubeCacheMaxSizeMb = 512;

    /** Maximum number of temporary YouTube tracks to retain in cache. */
    public int youtubeCacheMaxTracks = 24;

    /** Master volume from 0.0 to 1.0. */
    public float volume = 0.8f;

    /** Last non-zero volume used when toggling mute. */
    public float lastNonZeroVolume = 0.8f;

    /** Volume change amount used by player controls. */
    public float volumeStep = 0.1f;

    /** Playback mode: SEQUENTIAL, REPEAT_ONE, REPEAT_ALL, SHUFFLE. */
    public String playbackMode = "SEQUENTIAL";

    /** Directory to scan for local music files. Empty = .minecraft/xmusic/local. */
    public String localMusicDirectory = "";

    // ── Spotify (PKCE OAuth — tokens auto-managed) ──────────────────────
    public String spotifyAccessToken = "";
    public String spotifyRefreshToken = "";
    public long spotifyTokenExpiresAt = 0L;

    /** If true, the setup prompt popup will never show again (user clicked Skip). */
    public boolean setupPromptSkipped = false;

    // ── HUD Settings ────────────────────────────────────────────────────
    /** Whether the mini-player HUD overlay is visible in-game. */
    public boolean hudEnabled = true;

    /** HUD position preset: TOP_CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT. Used when hudX/hudY are -1. */
    public String hudPosition = "TOP_CENTER";

    /** Custom HUD X position in pixels. -1 = use preset position. */
    public int hudX = -1;

    /** Custom HUD Y position in pixels. -1 = use preset position. */
    public int hudY = -1;

    /** Auto-hide HUD after N seconds of inactivity (0 = always visible). */
    public int hudAutoHideSeconds = 3;

    /** Show "Now Playing" toast notification when track changes. */
    public boolean showNowPlayingToast = true;

    // ── Auto-Resume ──────────────────────────────────────────────────────
    /** Whether to auto-resume last playing track on game restart. */
    public boolean autoResume = true;

    /** Last playing track ID for auto-resume. */
    public String resumeTrackId = "";

    /** Last playing track source ID for auto-resume. */
    public String resumeSourceId = "";

    /** Last playing track title for auto-resume. */
    public String resumeTrackTitle = "";

    /** Last playing track artist for auto-resume. */
    public String resumeTrackArtist = "";

    /** Last playing track native URI for auto-resume. */
    public String resumeTrackNativeUri = "";

    /** Last playing track remote URI for auto-resume. */
    public String resumeTrackRemoteUri = "";

    /** Last playing track external URL for auto-resume. */
    public String resumeTrackExternalUrl = "";

    /** Last playing track playback type for auto-resume (NATIVE/REMOTE/EXTERNAL). */
    public String resumeTrackPlaybackType = "";

    /** Position in ms when the game was closed. */
    public long resumePositionMs = 0;

    /** Whether the track was playing (vs paused) when the game was closed. */
    public boolean resumeWasPlaying = false;

    // ── Locale ──────────────────────────────────────────────────────────
    /** Language code for i18n (e.g. "en", "es", "fr", "de", "ja", "zh"). */
    public String locale = "en";

    // ── Animation Settings ──────────────────────────────────────────────
    /** Whether GUI intro/outro and hover animations are enabled. */
    public boolean animationsEnabled = true;

    /** Animation speed multiplier: 0.5 = slow, 1.0 = normal, 2.0 = fast. */
    public float animationSpeed = 3.0f;

}
