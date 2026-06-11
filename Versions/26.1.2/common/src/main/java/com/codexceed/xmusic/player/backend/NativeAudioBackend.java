package com.codexceed.xmusic.player.backend;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.audio.AudioEventListener;
import com.codexceed.xmusic.audio.AudioPlayer;
import com.codexceed.xmusic.audio.AudioTrack;
import com.codexceed.xmusic.player.PlayerFacade;
import com.codexceed.xmusic.player.PlayerState;
import com.codexceed.xmusic.player.TrackRefMapper;
import com.codexceed.xmusic.source.PlaybackType;
import com.codexceed.xmusic.source.TrackRef;

/**
 * Playback backend that adapts the existing native AudioPlayer.
 * Routes track-ended events through PlayerFacade so loop/autoplay logic
 * is handled consistently across all backends.
 */
public final class NativeAudioBackend implements PlaybackBackend, AudioEventListener {
    private final AudioPlayer player = AudioPlayer.getInstance();
    private volatile boolean handlingTrackEnd = false;

    public NativeAudioBackend() {
        // Suppress AudioPlayer's internal auto-advance â€” we route through the facade instead
        player.setSuppressAutoAdvance(true);
        player.addListener(this);
    }

    @Override
    public String getId() {
        return "native";
    }

    @Override
    public boolean supports(TrackRef track) {
        return track != null && track.getPlaybackType() == PlaybackType.NATIVE;
    }

    @Override
    public boolean play(TrackRef track) {
        if (!supports(track)) {
            XMusic.LOGGER.warn("[Native] play() rejected track '{}' â€” playbackType={}, sourceId={}",
                    track != null ? track.getDisplayName() : "null",
                    track != null ? track.getPlaybackType() : "null",
                    track != null ? track.getSourceId() : "null");
            return false;
        }

        String uri = track.getNativeUri();
        if (uri == null || uri.isEmpty()) {
            XMusic.LOGGER.error("[Native] play() rejected track '{}' â€” nativeUri is empty (file path missing)",
                    track.getDisplayName());
            return false;
        }

        // Try to find this track in the existing queue first (preserves queue for next/prev)
        int existingIndex = player.findTrackIndex(uri);
        if (existingIndex >= 0) {
            XMusic.LOGGER.info("[Native] playAtIndex: index={} uri='{}'", existingIndex, uri);
            player.playAtIndex(existingIndex);
            return true;
        }

        // Not in queue â€” play as single (creates queue of 1)
        var audioTrack = TrackRefMapper.toAudioTrack(track);
        XMusic.LOGGER.info("[Native] playSingle: '{}' uri='{}'", audioTrack.getDisplayName(), audioTrack.getUri());
        player.playSingle(audioTrack);
        return true;
    }

    @Override
    public void pause() {
        player.pause();
    }

    @Override
    public void resume() {
        player.resume();
    }

    @Override
    public void stop() {
        player.clearQueue();
    }

    @Override
    public void seek(long positionMs) {
        player.seek(positionMs);
    }

    @Override
    public void setVolume(float volume) {
        player.setVolume(volume);
    }

    @Override
    public void tick() {
        player.tick();
    }

    @Override
    public PlayerState snapshot() {
        TrackRef currentTrack = TrackRefMapper.fromAudioTrack(player.getCurrentTrack());
        // Use AudioTrack's duration (from actual decode) if available, otherwise TrackRef estimate,
        // otherwise resolved duration from file size + audio format
        long durationMs = 0L;
        var at = player.getCurrentTrack();
        if (at != null && at.getDurationMs() > 0) {
            durationMs = at.getDurationMs();
        } else if (currentTrack != null && currentTrack.getDurationMs() > 0) {
            durationMs = currentTrack.getDurationMs();
        } else {
            long resolved = com.codexceed.xmusic.audio.AudioEngine.getInstance().getResolvedDurationMs();
            if (resolved > 0) durationMs = resolved;
        }

        return new PlayerState(
                getId(),
                currentTrack,
                player.isPlaying(),
                player.isPaused(),
                player.getPosition(),
                durationMs,
                player.getVolume(),
                player.getPlaybackMode(),
                player.getCurrentIndex(),
                player.getQueueSize());
    }

    // â”€â”€ AudioEventListener â€” route track-ended through facade â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    public void onTrackEnded(AudioTrack track) {
        if (handlingTrackEnd) return;
        handlingTrackEnd = true;

        PlayerFacade facade = PlayerFacade.getInstance();
        PlayerState state = facade.snapshot();

        // Only handle if we are the active backend
        if (!"native".equals(state.getBackendId())) {
            handlingTrackEnd = false;
            return;
        }

        XMusic.LOGGER.info("[Native] Track ended naturally: {}. Routing through facade.",
                track != null ? track.getDisplayName() : "unknown");

        try {
            // Check loop first â€” if looping, replay current track
            if (state.isLooping()) {
                facade.replayCurrentTrackFromBackend();
            } else if (facade.isAutoplay()) {
                facade.next();
            } else {
                // Neither loop nor autoplay â€” just stop
                facade.stop();
            }
        } catch (Exception e) {
            XMusic.LOGGER.error("[Native] Auto-advance after track ended failed", e);
        } finally {
            handlingTrackEnd = false;
        }
    }

    @Override
    public void onTrackStarted(AudioTrack track) {}

    @Override
    public void onProgress(long positionMs, long durationMs) {}

    @Override
    public void onPaused() {}

    @Override
    public void onResumed() {}

    @Override
    public void onStopped() {}

    @Override
    public void onError(String message, Exception exception) {}

    @Override
    public void onVolumeChanged(float volume) {}

    @Override
    public void onPlaybackModeChanged(com.codexceed.xmusic.audio.PlaybackMode mode) {}

    @Override
    public void onBuffering(AudioTrack track) {}
}
