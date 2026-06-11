package com.codexceed.xmusic.source;

/**
 * Lightweight normalized playlist model.
 */
public final class SourcePlaylist {
    private final String id;
    private final String sourceId;
    private final String name;
    private final int trackCount;

    public SourcePlaylist(String id, String sourceId, String name, int trackCount) {
        this.id = id;
        this.sourceId = sourceId;
        this.name = name;
        this.trackCount = trackCount;
    }

    public String getId() {
        return id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getName() {
        return name;
    }

    public int getTrackCount() {
        return trackCount;
    }
}
