package com.codexceed.xmusic.source.youtube;

import com.codexceed.xmusic.player.TrackRefMapper;
import com.codexceed.xmusic.service.youtube.YouTubeService;
import com.codexceed.xmusic.source.MusicSource;
import com.codexceed.xmusic.source.SourceCapability;
import com.codexceed.xmusic.source.SourcePlaylist;
import com.codexceed.xmusic.source.TrackRef;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * MusicSource adapter over the legacy YouTube service.
 */
public final class YouTubeMusicSourceAdapter implements MusicSource {
    private static final Set<SourceCapability> CAPABILITIES = EnumSet.of(
            SourceCapability.CAN_SEARCH,
            SourceCapability.CAN_PLAY_REMOTE);

    private final YouTubeService service;

    public YouTubeMusicSourceAdapter(YouTubeService service) {
        this.service = service;
    }

    @Override
    public String getId() {
        return "youtube";
    }

    @Override
    public String getDisplayName() {
        return "YouTube";
    }

    @Override
    public Set<SourceCapability> getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public CompletableFuture<Boolean> authenticate() {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public void refreshAuthState() {
        // YouTube search does not require an auth handshake in the current product flow.
    }

    @Override
    public List<TrackRef> getCachedTracks() {
        return mapTracks(service.getCachedTracks());
    }

    @Override
    public List<SourcePlaylist> getCachedPlaylists() {
        return new ArrayList<>();
    }

    @Override
    public CompletableFuture<List<TrackRef>> getLibraryTracks() {
        return CompletableFuture.completedFuture(new ArrayList<>());
    }

    @Override
    public CompletableFuture<List<SourcePlaylist>> getPlaylists() {
        return CompletableFuture.completedFuture(new ArrayList<>());
    }

    @Override
    public CompletableFuture<List<TrackRef>> getPlaylistTracks(String playlistId) {
        return CompletableFuture.completedFuture(new ArrayList<>());
    }

    @Override
    public CompletableFuture<List<TrackRef>> search(String query) {
        return service.search(query).thenApply(this::mapTracks);
    }

    private List<TrackRef> mapTracks(List<com.codexceed.xmusic.audio.AudioTrack> tracks) {
        List<TrackRef> mapped = new ArrayList<>(tracks.size());
        for (com.codexceed.xmusic.audio.AudioTrack track : tracks) {
            mapped.add(TrackRefMapper.fromAudioTrack(track));
        }
        return mapped;
    }
}
