package com.codexceed.xmusic.service;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.lavaplayer.LavaPlayerBackend;
import com.codexceed.xmusic.lavaplayer.LavaPlayerEngine;
import com.codexceed.xmusic.lavaplayer.LavaSearchService;
import com.codexceed.xmusic.player.backend.YouTubeNativeBackend;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.service.local.LocalMusicService;
import com.codexceed.xmusic.service.spotify.SpotifyAuthService;
import com.codexceed.xmusic.service.spotify.SpotifyMusicSourceAdapter;
import com.codexceed.xmusic.service.spotify.SpotifySearchService;
import com.codexceed.xmusic.service.youtube.YouTubeAudioResolver;
import com.codexceed.xmusic.service.youtube.YouTubeDownloadManager;
import com.codexceed.xmusic.service.youtube.YouTubeService;
import com.codexceed.xmusic.service.youtube.YouTubeStreamResolver;
import com.codexceed.xmusic.service.youtube.YouTubeToolManager;
import com.codexceed.xmusic.source.SourceRegistry;
import com.codexceed.xmusic.source.local.LocalMusicSourceAdapter;
import com.codexceed.xmusic.source.youtube.YouTubeMusicSourceAdapter;

/**
 * Central service manager for the production baseline.
 *
 * <p>The active product path is native local playback plus native YouTube
 * search/playback.
 */
public final class ServiceManager {
    private static LavaPlayerBackend lavaPlayerBackend;
    private static LavaSearchService lavaSearchService;

    private static YouTubeAudioResolver youtubeAudioResolver;
    private static YouTubeToolManager youtubeToolManager;
    private static YouTubeDownloadManager youtubeDownloadManager;
    private static YouTubeStreamResolver youtubeStreamResolver;
    private static YouTubeService youtubeService;
    private static YouTubeNativeBackend youtubeNativeBackend;
    private static LocalMusicService localMusicService;
    private static SourceRegistry sourceRegistry;
    private static SpotifyAuthService spotifyAuth;
    private static SpotifySearchService spotifySearch;
    private static SpotifyMusicSourceAdapter spotifySource;

    private ServiceManager() {}

    public static void init() {
        XMusic.LOGGER.info("Initializing music services...");

        LavaPlayerEngine lavaEngine = LavaPlayerEngine.getInstance();
        lavaPlayerBackend = new LavaPlayerBackend();
        lavaEngine.addListener(lavaPlayerBackend);
        lavaSearchService = new LavaSearchService(lavaEngine);

        youtubeAudioResolver = new YouTubeAudioResolver();
        youtubeToolManager = new YouTubeToolManager();
        youtubeDownloadManager = new YouTubeDownloadManager(youtubeToolManager);
        youtubeStreamResolver = new YouTubeStreamResolver(youtubeToolManager);
        youtubeService = new YouTubeService(youtubeDownloadManager, youtubeToolManager);
        youtubeNativeBackend = new YouTubeNativeBackend(youtubeDownloadManager);
        localMusicService = new LocalMusicService();
        sourceRegistry = new SourceRegistry();

        sourceRegistry.register(new YouTubeMusicSourceAdapter(youtubeService));
        sourceRegistry.register(new LocalMusicSourceAdapter(localMusicService));

        // Spotify (Client Credentials — free, no user login, no Premium)
        spotifyAuth = new SpotifyAuthService();
        spotifySearch = new SpotifySearchService(spotifyAuth);
        spotifySource = new SpotifyMusicSourceAdapter(spotifyAuth, spotifySearch);
        sourceRegistry.register(spotifySource);

        localMusicService.scanAsync();
        youtubeToolManager.refreshStatusAsync();

        XMusic.LOGGER.info("Music services initialized.");
    }

    public static LavaPlayerBackend getLavaPlayerBackend() {
        return lavaPlayerBackend;
    }

    public static LavaSearchService getLavaSearch() {
        return lavaSearchService;
    }

    public static YouTubeService getYouTube() {
        return youtubeService;
    }

    public static YouTubeAudioResolver getYouTubeResolver() {
        return youtubeAudioResolver;
    }

    public static YouTubeDownloadManager getYouTubeDownloader() {
        return youtubeDownloadManager;
    }

    public static YouTubeStreamResolver getYouTubeStreamResolver() {
        return youtubeStreamResolver;
    }

    public static YouTubeToolManager getYouTubeToolManager() {
        return youtubeToolManager;
    }

    public static YouTubeNativeBackend getYouTubeNativeBackend() {
        return youtubeNativeBackend;
    }

    public static LocalMusicService getLocalMusic() {
        return localMusicService;
    }

    public static SourceRegistry getSourceRegistry() {
        return sourceRegistry;
    }

    public static SpotifyAuthService getSpotifyAuth() {
        return spotifyAuth;
    }

    public static SpotifySearchService getSpotifySearch() {
        return spotifySearch;
    }

    public static SpotifyMusicSourceAdapter getSpotifySource() {
        return spotifySource;
    }

    /** No-op — Client Credentials tokens are auto-managed, no persistence needed. */
    public static void saveSpotifyTokens() {}
}
