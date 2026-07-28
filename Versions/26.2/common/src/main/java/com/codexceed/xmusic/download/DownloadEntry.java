package com.codexceed.xmusic.download;

import com.codexceed.xmusic.source.TrackRef;

/**
 * Represents a single download entry with state, progress, and process handle.
 */
public class DownloadEntry {
    public TrackRef track;
    public volatile DownloadState state = DownloadState.NONE;
    public volatile float progress = 0f;
    public volatile String error = null;
    public volatile Process process = null;

    public DownloadEntry(TrackRef track) {
        this.track = track;
    }

    public String getKey() {
        return track.getId() + "|" + track.getSourceId();
    }
}
