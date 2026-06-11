package com.codexceed.xmusic.lavaplayer;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.audio.PlaybackMode;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.player.PlayerState;
import com.codexceed.xmusic.player.backend.PlaybackBackend;
import com.codexceed.xmusic.source.TrackRef;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Playback backend powered by LavaPlayer + SourceDataLine.
 *
 * <p>All audio output happens on the XMusic-AudioOutput daemon thread via
 * {@link LavaPlayerEngine}. No OpenAL, no pipes, no render-thread involvement.
 *
 * <h3>State flow</h3>
 * <pre>
 *   IDLE Ã¢â€ â€™ play() Ã¢â€ â€™ RESOLVING Ã¢â€ â€™ (loadItem callback) Ã¢â€ â€™ engine.startTrack() Ã¢â€ â€™ PLAYING
 *   PLAYING Ã¢â€ â€™ pause() Ã¢â€ â€™ PAUSED Ã¢â€ â€™ resume() Ã¢â€ â€™ PLAYING
 *   PLAYING Ã¢â€ â€™ stop() Ã¢â€ â€™ IDLE
 *   RESOLVING Ã¢â€ â€™ play(new track) Ã¢â€ â€™ old callback discarded (generation mismatch)
 * </pre>
 *
 * <h3>snapshot() reads from LavaPlayerEngine only</h3>
 * No AudioEngine/OpenAL dependency. Position comes from LavaPlayer's own
 * per-track position tracker, which is drift-free.
 */
public final class LavaPlayerBackend extends AudioEventAdapter implements PlaybackBackend {

    private final LavaPlayerEngine engine = LavaPlayerEngine.getInstance();
    private final AtomicInteger generation = new AtomicInteger(0);

    private volatile TrackRef currentTrackRef;
    private volatile boolean resolving = false;
    private volatile PlayerFacade facade;

    public void setFacade(PlayerFacade facade) {
        this.facade = facade;
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬ PlaybackBackend Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    @Override
    public String getId() { return "lavaplayer"; }

    @Override
    public boolean supports(TrackRef track) {
        if (track == null) return false;
        String src = track.getSourceId();
        return "youtube".equals(src)
                || "soundcloud".equals(src)
                || "http".equals(src)
                || "spotify".equals(src);
    }

    @Override
    public boolean play(TrackRef track) {
        if (track == null) return false;

        int gen = generation.incrementAndGet();
        currentTrackRef = track;
        resolving = true;

        // Sync volume to LavaPlayer in case we're switching from another backend
        engine.setVolume(Math.round(ConfigManager.get().volume * 100f));

        String uri = resolveUri(track);
        XMusic.LOGGER.info("[LP-{}] Loading: {} Ã¢â€ â€™ {}", gen, track.getDisplayName(), uri);

        engine.getManager().loadItem(uri, new AudioLoadResultHandler() {

            @Override
            public void trackLoaded(AudioTrack lavaTrack) {
                if (isStale(gen)) return;
                XMusic.LOGGER.info("[LP-{}] Starting: {}", gen, lavaTrack.getInfo().title);

                // Update display track with resolved metadata (title/artist may differ from search)
                var info = lavaTrack.getInfo();
                currentTrackRef = new TrackRef.Builder()
                        .id(lavaTrack.getIdentifier())
                        .sourceId(track.getSourceId())
                        .title(info.title != null ? info.title : track.getTitle())
                        .artist(info.author != null ? info.author : track.getArtist())
                        .album(track.getAlbum())
                        .durationMs(info.length > 0 ? info.length : track.getDurationMs())
                        .artworkUrl(info.artworkUrl != null ? info.artworkUrl : track.getArtworkUrl())
                        .playbackType(track.getPlaybackType())
                        .remoteUri(lavaTrack.getIdentifier())
                        .externalUrl(info.uri != null ? info.uri : track.getExternalUrl())
                        .build();

                engine.startTrack(lavaTrack);
                resolving = false;
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (isStale(gen)) return;
                AudioTrack selected = playlist.getSelectedTrack();
                if (selected == null && !playlist.getTracks().isEmpty()) {
                    selected = playlist.getTracks().get(0);
                }
                if (selected != null) trackLoaded(selected);
                else noMatches();
            }

            @Override
            public void noMatches() {
                if (isStale(gen)) return;
                resolving = false;
                currentTrackRef = null;
                XMusic.LOGGER.warn("[LP-{}] No matches for: {}", gen, uri);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                if (isStale(gen)) return;
                resolving = false;
                XMusic.LOGGER.error("[LP-{}] Load failed: {}", gen, exception.getMessage());
            }
        });

        return true;
    }

    @Override
    public void pause() {
        engine.setPaused(true);
    }

    @Override
    public void resume() {
        engine.setPaused(false);
    }

    @Override
    public void stop() {
        generation.incrementAndGet();
        resolving = false;
        currentTrackRef = null;
        engine.stopTrack();
    }

    @Override
    public void seek(long positionMs) {
        AudioTrack playing = engine.getPlayer().getPlayingTrack();
        if (playing != null && playing.isSeekable()) {
            playing.setPosition(positionMs);
            engine.requestFlush(); // flush SDL buffer for instant seek response
        }
    }

    @Override
    public void setVolume(float volume) {
        engine.setVolume(Math.round(volume * 100f));
    }

    @Override
    public void tick() {
        // No-op: LavaPlayer + SourceDataLine runs on its own thread
    }

    @Override
    public PlayerState snapshot() {
        TrackRef display = currentTrackRef;

        int currentIndex = facade != null ? facade.getCurrentIndex() : 0;
        int queueSize    = facade != null ? facade.getQueue().size() : 0;
        float volume     = ConfigManager.get().volume;
        PlaybackMode mode = facade != null ? facade.getPlaybackMode() : PlaybackMode.SEQUENTIAL;

        // While resolving: show the track name, "Connecting..." status
        if (resolving) {
            return new PlayerState(
                    getId(), display,
                    false, false,
                    0L,
                    display != null ? display.getDurationMs() : 0L,
                    volume, mode, currentIndex, queueSize);
        }

        // No active track Ã¢â€ â€™ idle
        if (display == null) {
            return PlayerState.idle();
        }

        // Read state from LavaPlayer engine (not AudioEngine)
        boolean isPlaying = engine.isActuallyPlaying();
        boolean isPaused  = engine.isPaused() && engine.getPlayer().getPlayingTrack() != null;
        long positionMs   = engine.getPositionMs();
        long durationMs   = engine.getDurationMs();

        // Use the TrackRef's duration if LavaPlayer reports 0 (e.g. live stream)
        if (durationMs <= 0 && display.getDurationMs() > 0) {
            durationMs = display.getDurationMs();
        }

        return new PlayerState(
                getId(), display,
                isPlaying, isPaused,
                positionMs, durationMs,
                volume, mode, currentIndex, queueSize);
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬ Auto-advance on track end Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    @Override
    public void onTrackEnd(com.sedmelluq.discord.lavaplayer.player.AudioPlayer player,
                           AudioTrack track,
                           AudioTrackEndReason endReason) {
        if (endReason == AudioTrackEndReason.FINISHED || endReason == AudioTrackEndReason.LOAD_FAILED) {
            int capturedGen = generation.get();
            XMusic.LOGGER.info("[LP] Track ended ({}): {}", endReason,
                    track != null ? track.getInfo().title : "null");
            // Advance to next track on a separate thread to avoid blocking LavaPlayer's event loop
            Thread t = new Thread(() -> {
                if (generation.get() == capturedGen && facade != null) {
                    facade.next();
                }
            }, "XMusic-LavaAdvance");
            t.setDaemon(true);
            t.start();
        }
    }

    // Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬ URI Resolution Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬

    private String resolveUri(TrackRef track) {
        // Spotify: resolve ISRC â†’ YouTube search
        if ("spotify".equals(track.getSourceId())) {
            String isrc = track.getRemoteUri();
            if (isrc != null && !isrc.isBlank()) {
                return "ytsearch:" + isrc + " " + track.getArtist();
            }
            return "ytsearch:" + track.getTitle() + " " + track.getArtist();
        }

        // 1. Direct URL (e.g. YouTube URL, SoundCloud URL, HTTP stream)
        String externalUrl = track.getExternalUrl();
        if (externalUrl != null && !externalUrl.isBlank()) return externalUrl;

        // 2. Remote URI (may be a bare YouTube video ID or full URL)
        String remoteUri = track.getRemoteUri();
        if (remoteUri != null && !remoteUri.isBlank()) {
            // Bare 11-char YouTube video ID
            if (!remoteUri.startsWith("http") && remoteUri.length() == 11) {
                return "https://www.youtube.com/watch?v=" + remoteUri;
            }
            return remoteUri;
        }

        // 3. Last resort: YouTube text search
        return "ytsearch:" + track.getTitle() + " " + track.getArtist();
    }

    private boolean isStale(int gen) {
        return generation.get() != gen;
    }
}
