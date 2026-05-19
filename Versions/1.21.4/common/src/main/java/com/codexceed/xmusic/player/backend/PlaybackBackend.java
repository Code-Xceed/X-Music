package com.codexceed.xmusic.player.backend;

import com.codexceed.xmusic.player.PlayerState;
import com.codexceed.xmusic.source.TrackRef;

/**
 * Contract for any playback runtime used by the player facade.
 */
public interface PlaybackBackend {
    String getId();

    boolean supports(TrackRef track);

    boolean play(TrackRef track);

    void pause();

    void resume();

    void stop();

    void seek(long positionMs);

    void setVolume(float volume);

    void tick();

    PlayerState snapshot();
}
