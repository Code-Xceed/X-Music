package com.codexceed.xmusic.source.local;

import com.codexceed.xmusic.player.TrackRefMapper;
import com.codexceed.xmusic.service.local.LocalMusicService;
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
 * MusicSource adapter over the legacy local music service.
 */
public final class LocalMusicSourceAdapter implements MusicSource {
    private static final Set<SourceCapability> CAPABILITIES = EnumSet.of(
            SourceCapability.CAN_BROWSE_LIBRARY,
            SourceCapability.CAN_SEARCH,
            SourceCapability.CAN_PLAY_NATIVE,
            SourceCapability.CAN_SEEK);

    private final LocalMusicService service;

    public LocalMusicSourceAdapter(LocalMusicService service) {
        this.service = service;
    }

    @Override
    public String getId() {
        return "local";
    }

    @Override
    public String getDisplayName() {
        return "Local";
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
        // Local source requires no auth.
    }

    @Override
    public List<TrackRef> getCachedTracks() {
        return mapTracks(service.getTracks());
    }

    @Override
    public List<SourcePlaylist> getCachedPlaylists() {
        return new ArrayList<>();
    }

    @Override
    public CompletableFuture<List<TrackRef>> getLibraryTracks() {
        return CompletableFuture.supplyAsync(this::getCachedTracks);
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
        return CompletableFuture.supplyAsync(() -> {
            String normalized = query == null ? "" : query.trim().toLowerCase();
            List<TrackRef> results = new ArrayList<>();
            for (TrackRef track : getCachedTracks()) {
                if (normalized.isEmpty()
                        || track.getTitle().toLowerCase().contains(normalized)
                        || track.getArtist().toLowerCase().contains(normalized)
                        || track.getAlbum().toLowerCase().contains(normalized)) {
                    results.add(track);
                }
            }
            return results;
        });
    }

    private List<TrackRef> mapTracks(List<com.codexceed.xmusic.audio.AudioTrack> tracks) {
        List<TrackRef> mapped = new ArrayList<>(tracks.size());
        for (com.codexceed.xmusic.audio.AudioTrack track : tracks) {
            mapped.add(TrackRefMapper.fromAudioTrack(track));
        }
        return mapped;
    }
}
