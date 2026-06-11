package com.codexceed.xmusic.audio;

/**
 * Playback mode for the audio player queue.
 */
public enum PlaybackMode {
    /** Play tracks in order, stop at the end. */
    SEQUENTIAL,

    /** Repeat the current track forever. */
    REPEAT_ONE,

    /** Repeat the entire queue when it ends. */
    REPEAT_ALL,

    /** Shuffle the queue randomly. */
    SHUFFLE;

    /**
     * Cycle to the next mode:
     * SEQUENTIAL -> REPEAT_ALL -> REPEAT_ONE -> SHUFFLE -> SEQUENTIAL
     */
    public PlaybackMode next() {
        switch (this) {
            case SEQUENTIAL:
                return REPEAT_ALL;
            case REPEAT_ALL:
                return REPEAT_ONE;
            case REPEAT_ONE:
                return SHUFFLE;
            case SHUFFLE:
                return SEQUENTIAL;
            default:
                return SEQUENTIAL;
        }
    }

    /**
     * Get a user-friendly display name.
     */
    public String getDisplayName() {
        switch (this) {
            case SEQUENTIAL:
                return "Play in Order";
            case REPEAT_ONE:
                return "Repeat One";
            case REPEAT_ALL:
                return "Repeat All";
            case SHUFFLE:
                return "Shuffle";
            default:
                return "Unknown";
        }
    }

    /**
     * Get the compact label used by the UI and HUD.
     */
    public String getIcon() {
        switch (this) {
            case SEQUENTIAL:
                return ">";
            case REPEAT_ONE:
                return "R1";
            case REPEAT_ALL:
                return "R";
            case SHUFFLE:
                return "S";
            default:
                return "?";
        }
    }
}
