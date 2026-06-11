package com.codexceed.xmusic.audio;

/**
 * Callback interface for UI/HUD to observe audio engine state changes.
 */
public interface AudioEventListener {

    /**
     * Called when a new track starts playing.
     */
    default void onTrackStarted(AudioTrack track) {}

    /**
     * Called when the current track finishes playing.
     */
    default void onTrackEnded(AudioTrack track) {}

    /**
     * Called periodically with the current playback position.
     * @param positionMs current position in milliseconds
     * @param durationMs total duration in milliseconds
     */
    default void onProgress(long positionMs, long durationMs) {}

    /**
     * Called when playback is paused.
     */
    default void onPaused() {}

    /**
     * Called when playback resumes from a paused state.
     */
    default void onResumed() {}

    /**
     * Called when playback is stopped (queue finished or manual stop).
     */
    default void onStopped() {}

    /**
     * Called when volume changes.
     */
    default void onVolumeChanged(float volume) {}

    /**
     * Called when the playback mode changes.
     */
    default void onPlaybackModeChanged(PlaybackMode mode) {}

    /**
     * Called when an error occurs during playback.
     */
    default void onError(String message, Exception exception) {}

    /**
     * Called when a track is loading/buffering.
     */
    default void onBuffering(AudioTrack track) {}
}
