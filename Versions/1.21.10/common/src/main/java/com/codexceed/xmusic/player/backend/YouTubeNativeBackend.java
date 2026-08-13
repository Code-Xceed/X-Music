package com.codexceed.xmusic.player.backend;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.audio.AudioEngine;
import com.codexceed.xmusic.audio.AudioEventListener;
import com.codexceed.xmusic.audio.AudioPlayer;
import com.codexceed.xmusic.audio.AudioTrack;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.player.PlayerState;
import com.codexceed.xmusic.player.TrackRefMapper;
import com.codexceed.xmusic.service.ServiceManager;
import com.codexceed.xmusic.service.youtube.FfmpegPcmStream;
import com.codexceed.xmusic.service.youtube.YouTubeDownloadManager;
import com.codexceed.xmusic.service.youtube.YouTubeService;
import com.codexceed.xmusic.service.youtube.YouTubeStreamResolver;
import com.codexceed.xmusic.service.youtube.YouTubeToolManager;
import com.codexceed.xmusic.source.TrackRef;

import javax.sound.sampled.AudioInputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Playback backend for YouTube tracks with stream-first, zero-wait playback.
 *
 * <h3>Rapid Switching Safety</h3>
 * <ul>
 *   <li>Every {@link #play} call increments a generation counter. All async callbacks
 *       check this counter before proceeding â€” stale callbacks are silently discarded.</li>
 *   <li>{@link #onTrackEnded} captures the generation at fire time and verifies it
 *       hasn't changed before routing auto-advance, preventing phantom skips during
 *       rapid switching.</li>
 *   <li>FFmpeg streams from stale tracks are closed immediately when detected,
 *       killing the zombie process.</li>
 *   <li>{@code handlingTrackEnd} is always reset by {@link #play} to prevent the
 *       flag from getting stuck if the user clicks a new track during auto-advance.</li>
 * </ul>
 *
 * <h3>Playback Priority</h3>
 * <ol>
 *   <li><b>Cached file</b> â€” If a local .m4a/.mp3 exists, play via FFmpeg PCM.</li>
 *   <li><b>Stream-first</b> â€” Piped API / yt-dlp â†’ FFmpeg â†’ OpenAL.</li>
 *   <li><b>Download fallback</b> â€” Full yt-dlp download, then play.</li>
 * </ol>
 */
public final class YouTubeNativeBackend implements PlaybackBackend, AudioEventListener {
    private static final long DOWNLOAD_TIMEOUT_SECONDS = 60L;

    private final YouTubeDownloadManager downloadManager;
    private final AtomicInteger playGeneration = new AtomicInteger();
    private volatile TrackRef currentTrack;
    private volatile boolean resolving;
    private volatile boolean handlingTrackEnd = false;

    public YouTubeNativeBackend(YouTubeDownloadManager downloadManager) {
        this.downloadManager = downloadManager;
        AudioEngine.getInstance().addListener(this);
    }

    @Override
    public String getId() {
        return "youtube-native";
    }

    @Override
    public boolean supports(TrackRef track) {
        return track != null && "youtube".equals(track.getSourceId());
    }

    @Override
    public boolean play(TrackRef track) {
        if (track == null) {
            return false;
        }
        if (downloadManager == null) {
            reportFailure("YouTube download pipeline is not available.");
            return false;
        }

        // Increment generation FIRST â€” this instantly invalidates all in-flight
        // callbacks from previous tracks (resolver futures, FFmpeg opens, etc.)
        int generation = playGeneration.incrementAndGet();
        currentTrack = track;
        resolving = true;
        // Always reset â€” prevents stuck flag if user clicked during auto-advance
        handlingTrackEnd = false;
        AudioPlayer.getInstance().setSuppressAutoAdvance(true);
        AudioPlayer.getInstance().stop();

        XMusic.LOGGER.info("[YT-{}] Play requested: {}", generation, track.getDisplayName());

        // All slow work (stream resolution, FFmpeg launch) happens
        // on background threads. The render thread returns instantly from play().
        CompletableFuture.runAsync(() -> routePlayback(track, generation));
        return true;
    }

    /**
     * Main routing logic â€” runs on a BACKGROUND thread, never the render thread.
     * Checks caches and dispatches to the right playback path.
     */
    private void routePlayback(TrackRef originalTrack, int generation) {
        if (isStale(generation)) return;

        TrackRef track = originalTrack;

        // 1. If already cached locally, play from the file instantly.
        if (downloadManager.isCached(track)) {
            reportBuffering(track);
            playFromCachedFile(track, generation);
            return;
        }
        
        // 2. If streaming tools are available, always stream-first.
        YouTubeStreamResolver resolver = ServiceManager.getYouTubeStreamResolver();
        YouTubeToolManager toolManager = ServiceManager.getYouTubeToolManager();
        if (resolver != null && resolver.isStreamingAvailable()) {
            reportResolving(track);
            playFromStream(track, generation, resolver, toolManager);
            return;
        }
        
        // 3. Fallback: no ffmpeg available â€” must download first.
        reportDownloading(track);
        playFromDownload(track, generation);
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    //  AudioEventListener â€” intercept track-ended
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    public void onTrackEnded(AudioTrack track) {
        // Capture current state atomically
        TrackRef myTrack = currentTrack;
        int currentGen = playGeneration.get();

        // Only handle if we have an active track and aren't already advancing
        if (myTrack == null || handlingTrackEnd) {
            return;
        }

        // Verify we are the active backend
        PlayerFacade facade = PlayerFacade.getInstance();
        PlayerState state = facade.snapshot();
        if (!"youtube-native".equals(state.getBackendId())) {
            return;
        }

        handlingTrackEnd = true;
        XMusic.LOGGER.info("[YT-{}] Track ended naturally: {}. Auto-advancing.",
                currentGen, track != null ? track.getDisplayName() : "unknown");

        CompletableFuture.runAsync(() -> {
            try {
                // Re-check generation: if the user clicked a new track between the event
                // firing and this async handler running, don't advance.
                if (playGeneration.get() != currentGen) {
                    XMusic.LOGGER.debug("[YT] Auto-advance cancelled â€” user already switched tracks.");
                    return;
                }
                if (facade.shouldLoopCurrentTrack()) {
                    facade.replayCurrentTrackFromBackend();
                } else if (facade.isAutoplay() && !"home".equals(facade.getPlaybackContext())) {
                    facade.next();
                } else {
                    facade.stop();
                }
            } catch (Exception e) {
                XMusic.LOGGER.error("Auto-advance after YouTube track ended failed", e);
            } finally {
                handlingTrackEnd = false;
            }
        });
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    //  Stream-first playback path
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€


    private void playFromStream(TrackRef track, int generation,
                                YouTubeStreamResolver resolver, YouTubeToolManager toolManager) {
        resolver.resolve(track)
                .thenAccept(resolved -> {
                    if (isStale(generation)) {
                        XMusic.LOGGER.debug("[YT-{}] Discarding stale stream resolve for {}", generation, track.getDisplayName());
                        return;
                    }
                    if (resolved == null || resolved.url() == null || resolved.url().isBlank()) {
                        XMusic.LOGGER.warn("[YT-{}] Stream resolve returned null for {}; falling back to download.",
                                generation, track.getDisplayName());
                        reportDownloading(track);
                        playFromDownload(track, generation);
                        return;
                    }
                    try {
                        reportBuffering(track);
                        AudioInputStream pcm = FfmpegPcmStream.open(toolManager.getFfmpegExecutable(), resolved.url());

                        // Check staleness AFTER opening FFmpeg â€” if stale, kill the process immediately
                        if (isStale(generation)) {
                            pcm.close(); // Destroys FFmpeg process
                            XMusic.LOGGER.debug("[YT-{}] Closed stale FFmpeg process for {}", generation, track.getDisplayName());
                            return;
                        }

                        AudioTrack audioTrack = buildAudioTrack(track, resolved.url());
                        AudioEngine.getInstance().playPcmStream(audioTrack, pcm);
                        resolving = false;
                        XMusic.LOGGER.info("[YT-{}] Now streaming: {}", generation, track.getDisplayName());
                    } catch (Exception e) {
                        if (isStale(generation)) return;
                        XMusic.LOGGER.warn("[YT-{}] Stream playback failed for {}; falling back: {}",
                                generation, track.getDisplayName(), e.getMessage());
                        reportDownloading(track);
                        playFromDownload(track, generation);
                    }
                })
                .exceptionally(error -> {
                    if (isStale(generation)) return null;
                    XMusic.LOGGER.warn("[YT-{}] Stream resolve exception for {}; falling back: {}",
                            generation, track.getDisplayName(), resolveErrorMessage(error));
                    reportDownloading(track);
                    playFromDownload(track, generation);
                    return null;
                });
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    //  Cached-file playback path (instant)
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void playFromCachedFile(TrackRef track, int generation) {
        CompletableFuture<YouTubeDownloadManager.PreparedTrack> future = downloadManager.prepareForPlayback(track);
        future.completeOnTimeout(null, 10L, TimeUnit.SECONDS)
                .thenAccept(prepared -> {
                    if (isStale(generation)) return;
                    resolving = false;

                    if (prepared == null || prepared.localPath() == null) {
                        YouTubeStreamResolver resolver = ServiceManager.getYouTubeStreamResolver();
                        YouTubeToolManager toolManager = ServiceManager.getYouTubeToolManager();
                        if (resolver != null && resolver.isStreamingAvailable()) {
                            reportResolving(track);
                            playFromStream(track, generation, resolver, toolManager);
                        } else {
                            reportDownloading(track);
                            playFromDownload(track, generation);
                        }
                        return;
                    }

                    AudioTrack audioTrack = buildAudioTrack(track, prepared.localPath().toString());
                    reportBuffering(track);

                    if (playWithFfmpeg(audioTrack, prepared.localPath().toString(), generation)) {
                        XMusic.LOGGER.info("[YT-{}] Playing cached: {} from {}", generation, track.getDisplayName(), prepared.localPath());
                        return;
                    }

                    if (isStale(generation)) return;
                    reportFailure("FFmpeg playback failed or ffmpeg is missing.");
                })
                .exceptionally(error -> {
                    if (isStale(generation)) return null;
                    resolving = false;
                    reportFailure(resolveErrorMessage(error));
                    return null;
                });
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    //  Download-first playback path (last resort)
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void playFromDownload(TrackRef track, int generation) {
        CompletableFuture<YouTubeDownloadManager.PreparedTrack> future = downloadManager.prepareForPlayback(track);
        future.completeOnTimeout(null, DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .thenAccept(prepared -> {
                    if (isStale(generation)) return;
                    resolving = false;

                    if (prepared == null || prepared.localPath() == null) {
                        reportFailure("YouTube download timed out.");
                        return;
                    }

                    AudioTrack audioTrack = buildAudioTrack(track, prepared.localPath().toString());
                    reportBuffering(track);

                    if (playWithFfmpeg(audioTrack, prepared.localPath().toString(), generation)) {
                        XMusic.LOGGER.info("[YT-{}] Playing downloaded: {}", generation, track.getDisplayName());
                        return;
                    }

                    if (isStale(generation)) return;
                    reportFailure("FFmpeg playback failed or ffmpeg is missing.");
                })
                .exceptionally(error -> {
                    if (isStale(generation)) return null;
                    resolving = false;
                    reportFailure(resolveErrorMessage(error));
                    return null;
                });
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    //  Shared helpers
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private boolean playWithFfmpeg(AudioTrack audioTrack, String inputPath, int generation) {
        YouTubeToolManager toolManager = ServiceManager.getYouTubeToolManager();
        if (toolManager == null || !toolManager.hasFfmpeg() || toolManager.getFfmpegExecutable().isBlank()) {
            return false;
        }
        try {
            AudioInputStream pcm = FfmpegPcmStream.open(toolManager.getFfmpegExecutable(), inputPath);
            if (isStale(generation)) {
                pcm.close();
                return false;
            }
            AudioEngine.getInstance().playPcmStream(audioTrack, pcm);
            return true;
        } catch (Exception e) {
            XMusic.LOGGER.warn("FFmpeg PCM failed for {}: {}", inputPath, e.getMessage());
            return false;
        }
    }
    private AudioTrack buildAudioTrack(TrackRef track, String uri) {
        return new AudioTrack.Builder()
                .id(track.getId())
                .title(track.getTitle())
                .artist(track.getArtist())
                .album("YouTube")
                .durationMs(track.getDurationMs())
                .uri(uri)
                .albumArtUrl(track.getArtworkUrl())
                .source(AudioTrack.Source.YOUTUBE)
                .externalUrl(track.getExternalUrl())
                .build();
    }

    /**
     * Check if the given generation is stale (a newer play() was issued).
     * Uses only the generation counter â€” no track comparison needed since
     * generation is strictly monotonic.
     */
    private boolean isStale(int generation) {
        return generation != playGeneration.get();
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    //  Standard PlaybackBackend methods
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    public void pause() {
        AudioPlayer.getInstance().pause();
        if (currentTrack != null) reportPaused(currentTrack);
    }

    @Override
    public void resume() {
        AudioPlayer.getInstance().play();
        if (currentTrack != null) reportBuffering(currentTrack);
    }

    @Override
    public void stop() {
        playGeneration.incrementAndGet();
        resolving = false;
        currentTrack = null;
        handlingTrackEnd = false;
        AudioPlayer.getInstance().setSuppressAutoAdvance(false);
        AudioPlayer.getInstance().stop();
        reportStopped();
    }

    @Override
    public void seek(long positionMs) {
        // Seek not supported for streams
    }

    @Override
    public void setVolume(float volume) {
        AudioPlayer.getInstance().setVolume(volume);
    }

    @Override
    public void tick() {
        AudioPlayer.getInstance().tick();
    }

    @Override
    public PlayerState snapshot() {
        AudioEngine engine = AudioEngine.getInstance();
        AudioPlayer player = AudioPlayer.getInstance();

        TrackRef display = currentTrack;
        AudioTrack engineTrack = engine.getCurrentTrack();
        if (engineTrack != null && display == null) {
            display = TrackRefMapper.fromAudioTrack(engineTrack);
        }

        boolean stalled = engine.isStalled();
        if (stalled && currentTrack != null) {
            // Re-report buffering if the engine stalled mid-playback
            reportBuffering(currentTrack);
        }
        
        boolean isResolving = resolving || stalled;

        if (isResolving && display != null) {
            return new PlayerState(
                    getId(), display, false, false, engine.getPosition(),
                    display.getDurationMs(), player.getVolume(),
                    player.getPlaybackMode(), player.getCurrentIndex(), player.getQueue().size());
        }

        return new PlayerState(
                getId(), display, engine.isPlaying(), engine.isPaused(),
                engine.getPosition(),
                engineTrack != null ? engineTrack.getDurationMs() : (display != null ? display.getDurationMs() : 0L),
                player.getVolume(), player.getPlaybackMode(),
                player.getCurrentIndex(), player.getQueue().size());
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    //  Status reporting
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void reportDownloading(TrackRef t) {
        YouTubeService s = ServiceManager.getYouTube();
        if (s != null && t != null) s.reportNativeDownloading(t.getTitle());
    }
    private void reportResolving(TrackRef t) {
        YouTubeService s = ServiceManager.getYouTube();
        if (s != null && t != null) s.reportNativeResolving(t.getTitle());
    }
    private void reportBuffering(TrackRef t) {
        YouTubeService s = ServiceManager.getYouTube();
        if (s != null && t != null) s.reportNativeBuffering(t.getTitle());
    }
    private void reportPaused(TrackRef t) {
        YouTubeService s = ServiceManager.getYouTube();
        if (s != null && t != null) s.reportNativePaused(t.getTitle());
    }
    private void reportStopped() {
        YouTubeService s = ServiceManager.getYouTube();
        if (s != null) s.reportNativeStopped();
    }
    private void reportFailure(String msg) {
        YouTubeService s = ServiceManager.getYouTube();
        if (s != null) s.reportNativeFailure(msg);
        com.codexceed.xmusic.player.PlayerFacade.getInstance().setLastError(msg);
    }

    private String resolveErrorMessage(Throwable error) {
        Throwable c = error;
        while (c != null) {
            if (c.getMessage() != null && !c.getMessage().isBlank()) return c.getMessage();
            c = c.getCause();
        }
        return "YouTube pipeline failed.";
    }
}
