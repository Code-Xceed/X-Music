package com.codexceed.xmusic.player;

import com.codexceed.xmusic.audio.PlaybackMode;
import com.codexceed.xmusic.source.TrackRef;

/**
 * Immutable snapshot of the current player state.
 *
 * <p>Read by the GUI and HUD every frame. Contains everything needed to
 * render controls, progress bar, now-playing info, and navigation state.
 */
public final class PlayerState {
    private final String backendId;
    private final TrackRef currentTrack;
    private final boolean playing;
    private final boolean paused;
    private final long positionMs;
    private final long durationMs;
    private final float volume;
    private final PlaybackMode playbackMode;
    private final int currentIndex;
    private final int queueSize;

    // â”€â”€ Loop â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    /** 0=off, 3/5=repeat N times, -1=infinite */
    private final int loopCount;
    /** How many times the current track has played so far in this loop cycle */
    private final int loopIteration;

    // â”€â”€ History navigation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private final boolean canHistoryBack;
    private final boolean canHistoryForward;
    private final boolean autoplay;

    public PlayerState(
            String backendId,
            TrackRef currentTrack,
            boolean playing,
            boolean paused,
            long positionMs,
            long durationMs,
            float volume,
            PlaybackMode playbackMode,
            int currentIndex,
            int queueSize,
            int loopCount,
            int loopIteration,
            boolean canHistoryBack,
            boolean canHistoryForward,
            boolean autoplay) {
        this.backendId = backendId;
        this.currentTrack = currentTrack;
        this.playing = playing;
        this.paused = paused;
        this.positionMs = positionMs;
        this.durationMs = durationMs;
        this.volume = volume;
        this.playbackMode = playbackMode;
        this.currentIndex = currentIndex;
        this.queueSize = queueSize;
        this.loopCount = loopCount;
        this.loopIteration = loopIteration;
        this.canHistoryBack = canHistoryBack;
        this.canHistoryForward = canHistoryForward;
        this.autoplay = autoplay;
    }

    /** Backwards-compatible constructor */
    public PlayerState(
            String backendId,
            TrackRef currentTrack,
            boolean playing,
            boolean paused,
            long positionMs,
            long durationMs,
            float volume,
            PlaybackMode playbackMode,
            int currentIndex,
            int queueSize) {
        this(backendId, currentTrack, playing, paused, positionMs, durationMs,
                volume, playbackMode, currentIndex, queueSize,
                0, 0, false, false, true);
    }

    public static PlayerState idle() {
        return new PlayerState("none", null, false, false, 0L, 0L, 1.0f,
                PlaybackMode.SEQUENTIAL, -1, 0, 0, 0, false, false, true);
    }

    // â”€â”€ Getters â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public String getBackendId()       { return backendId; }
    public TrackRef getCurrentTrack()  { return currentTrack; }
    public boolean isPlaying()         { return playing; }
    public boolean isPaused()          { return paused; }
    public long getPositionMs()        { return positionMs; }
    public long getDurationMs()        { return durationMs; }
    public float getVolume()           { return volume; }
    public PlaybackMode getPlaybackMode() { return playbackMode; }
    public int getCurrentIndex()       { return currentIndex; }
    public int getQueueSize()          { return queueSize; }
    public int getLoopCount()          { return loopCount; }
    public int getLoopIteration()      { return loopIteration; }
    public boolean canHistoryBack()    { return canHistoryBack; }
    public boolean canHistoryForward() { return canHistoryForward; }
    public boolean isAutoplay()        { return autoplay; }

    /**
     * Display string for the loop mode button.
     * @return "â€”" (off), "Ã—3", "Ã—5", or "âˆž"
     */
    public String getLoopDisplay() {
        if (loopCount == 0)  return "\u2014";  // â€” (em dash = off)
        if (loopCount == -1) return "\u221E";  // âˆž
        return "\u00D7" + loopCount;            // Ã—3, Ã—5
    }

    /** True if loop is active (not off). */
    public boolean isLooping() {
        return loopCount != 0;
    }
}
