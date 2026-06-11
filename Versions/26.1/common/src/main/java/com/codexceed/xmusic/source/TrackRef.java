package com.codexceed.xmusic.source;

import java.util.Objects;

/**
 * Normalized track model shared by all sources and playback backends.
 */
public final class TrackRef {
    private final String id;
    private final String sourceId;
    private final String title;
    private final String artist;
    private final String album;
    private final long durationMs;
    private final String artworkUrl;
    private final PlaybackType playbackType;
    private final String nativeUri;
    private final String remoteUri;
    private final String externalUrl;

    private TrackRef(Builder builder) {
        this.id = builder.id;
        this.sourceId = builder.sourceId;
        this.title = builder.title;
        this.artist = builder.artist;
        this.album = builder.album;
        this.durationMs = builder.durationMs;
        this.artworkUrl = builder.artworkUrl;
        this.playbackType = builder.playbackType;
        this.nativeUri = builder.nativeUri;
        this.remoteUri = builder.remoteUri;
        this.externalUrl = builder.externalUrl;
    }

    public String getId() {
        return id;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getArtworkUrl() {
        return artworkUrl;
    }

    public PlaybackType getPlaybackType() {
        return playbackType;
    }

    public String getNativeUri() {
        return nativeUri;
    }

    public String getRemoteUri() {
        return remoteUri;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public String getDisplayName() {
        if (artist != null && !artist.isEmpty()) {
            return artist + " - " + title;
        }
        return title;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackRef)) return false;
        TrackRef trackRef = (TrackRef) o;
        return Objects.equals(id, trackRef.id) && Objects.equals(sourceId, trackRef.sourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sourceId);
    }

    public static class Builder {
        private String id = "";
        private String sourceId = "";
        private String title = "Unknown Track";
        private String artist = "Unknown Artist";
        private String album = "Unknown Album";
        private long durationMs = 0;
        private String artworkUrl = "";
        private PlaybackType playbackType = PlaybackType.NATIVE;
        private String nativeUri = "";
        private String remoteUri = "";
        private String externalUrl = "";

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder sourceId(String sourceId) {
            this.sourceId = sourceId;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder artist(String artist) {
            this.artist = artist;
            return this;
        }

        public Builder album(String album) {
            this.album = album;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder artworkUrl(String artworkUrl) {
            this.artworkUrl = artworkUrl;
            return this;
        }

        public Builder playbackType(PlaybackType playbackType) {
            this.playbackType = playbackType;
            return this;
        }

        public Builder nativeUri(String nativeUri) {
            this.nativeUri = nativeUri;
            return this;
        }

        public Builder remoteUri(String remoteUri) {
            this.remoteUri = remoteUri;
            return this;
        }

        public Builder externalUrl(String externalUrl) {
            this.externalUrl = externalUrl;
            return this;
        }

        public TrackRef build() {
            return new TrackRef(this);
        }
    }
}
