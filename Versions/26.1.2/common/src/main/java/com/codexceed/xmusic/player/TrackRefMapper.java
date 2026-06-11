package com.codexceed.xmusic.player;

import com.codexceed.xmusic.audio.AudioTrack;
import com.codexceed.xmusic.source.PlaybackType;
import com.codexceed.xmusic.source.TrackRef;

/**
 * Maps between legacy AudioTrack objects and normalized TrackRef objects.
 */
public final class TrackRefMapper {
    private TrackRefMapper() {}

    public static TrackRef fromAudioTrack(AudioTrack track) {
        if (track == null) return null;

        String sourceId;
        switch (track.getSource()) {
            case YOUTUBE:
                sourceId = "youtube";
                break;
            case LOCAL:
            default:
                sourceId = "local";
                break;
        }

        PlaybackType playbackType = track.getSource() == AudioTrack.Source.LOCAL
                ? PlaybackType.NATIVE
                : PlaybackType.REMOTE;

        return new TrackRef.Builder()
                .id(track.getId())
                .sourceId(sourceId)
                .title(track.getTitle())
                .artist(track.getArtist())
                .album(track.getAlbum())
                .durationMs(track.getDurationMs())
                .artworkUrl(track.getAlbumArtUrl())
                .playbackType(playbackType)
                .nativeUri(playbackType == PlaybackType.NATIVE ? track.getUri() : "")
                .remoteUri(playbackType == PlaybackType.REMOTE ? track.getUri() : "")
                .externalUrl(track.getExternalUrl())
                .build();
    }

    public static AudioTrack toAudioTrack(TrackRef track) {
        if (track == null) return null;

        AudioTrack.Source source;
        switch (track.getSourceId()) {
            case "youtube":
                source = AudioTrack.Source.YOUTUBE;
                break;
            case "local":
            default:
                source = AudioTrack.Source.LOCAL;
                break;
        }

        String uri = track.getPlaybackType() == PlaybackType.NATIVE
                ? track.getNativeUri()
                : track.getRemoteUri();

        return new AudioTrack.Builder()
                .id(track.getId())
                .title(track.getTitle())
                .artist(track.getArtist())
                .album(track.getAlbum())
                .durationMs(track.getDurationMs())
                .uri(uri != null ? uri : "")
                .albumArtUrl(track.getArtworkUrl())
                .externalUrl(track.getExternalUrl())
                .source(source)
                .build();
    }
}
