package com.codexceed.xmusic.source;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Normalized contract for all music sources.
 */
public interface MusicSource {
    String getId();

    String getDisplayName();

    Set<SourceCapability> getCapabilities();

    boolean isAuthenticated();

    CompletableFuture<Boolean> authenticate();

    void refreshAuthState();

    List<TrackRef> getCachedTracks();

    List<SourcePlaylist> getCachedPlaylists();

    CompletableFuture<List<TrackRef>> getLibraryTracks();

    CompletableFuture<List<SourcePlaylist>> getPlaylists();

    CompletableFuture<List<TrackRef>> getPlaylistTracks(String playlistId);

    CompletableFuture<List<TrackRef>> search(String query);
}
