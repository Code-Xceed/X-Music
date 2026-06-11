package com.codexceed.xmusic.audio;

/**
 * Represents a single music track from any source.
 * Immutable data class constructed via the Builder.
 */
public class AudioTrack {
    private final String id;
    private final String title;
    private final String artist;
    private final String album;
    private final long durationMs;
    private final String uri;
    private final String albumArtUrl;
    private final Source source;
    private final String externalUrl;

    public enum Source {
        LOCAL,
        YOUTUBE
    }

    private AudioTrack(Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.artist = builder.artist;
        this.album = builder.album;
        this.durationMs = builder.durationMs;
        this.uri = builder.uri;
        this.albumArtUrl = builder.albumArtUrl;
        this.source = builder.source;
        this.externalUrl = builder.externalUrl;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public long getDurationMs() { return durationMs; }
    public String getUri() { return uri; }
    public String getAlbumArtUrl() { return albumArtUrl; }
    public Source getSource() { return source; }
    public String getExternalUrl() { return externalUrl; }

    /**
     * @return Duration formatted as MM:SS or H:MM:SS
     */
    public String getFormattedDuration() {
        long totalSeconds = durationMs / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * @return Display name: "Artist - Title" or just "Title" if no artist.
     */
    public String getDisplayName() {
        if (artist != null && !artist.isEmpty()) {
            return artist + " - " + title;
        }
        return title;
    }

    @Override
    public String toString() {
        return "AudioTrack{" + source + ": " + getDisplayName() + " [" + getFormattedDuration() + "]}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AudioTrack that = (AudioTrack) o;
        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    /**
     * Builder for immutable {@link AudioTrack} instances.
     */
    public static class Builder {
        private String id = "";
        private String title = "Unknown Track";
        private String artist = "Unknown Artist";
        private String album = "Unknown Album";
        private long durationMs = 0;
        private String uri = "";
        private String albumArtUrl = "";
        private Source source = Source.LOCAL;
        private String externalUrl = "";

        public Builder id(String id) { this.id = id; return this; }
        public Builder title(String title) { this.title = title; return this; }
        public Builder artist(String artist) { this.artist = artist; return this; }
        public Builder album(String album) { this.album = album; return this; }
        public Builder durationMs(long durationMs) { this.durationMs = durationMs; return this; }
        public Builder uri(String uri) { this.uri = uri; return this; }
        public Builder albumArtUrl(String url) { this.albumArtUrl = url; return this; }
        public Builder source(Source source) { this.source = source; return this; }
        public Builder externalUrl(String url) { this.externalUrl = url; return this; }

        public AudioTrack build() {
            return new AudioTrack(this);
        }
    }
}
