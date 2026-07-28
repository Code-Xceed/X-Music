package com.codexceed.xmusic.service.spotify;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.source.MusicSource;
import com.codexceed.xmusic.source.SourceCapability;
import com.codexceed.xmusic.source.SourcePlaylist;
import com.codexceed.xmusic.source.TrackRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * MusicSource adapter for Spotify — Client Credentials (free, no user login).
 */
public final class SpotifyMusicSourceAdapter implements MusicSource {

    private static final Set<SourceCapability> CAPABILITIES = EnumSet.of(
            SourceCapability.CAN_SEARCH,
            SourceCapability.CAN_BROWSE_PLAYLISTS,
            SourceCapability.CAN_PLAY_REMOTE);

    private final SpotifyAuthService auth;
    private final SpotifySearchService search;

    public SpotifyMusicSourceAdapter(SpotifyAuthService auth, SpotifySearchService search) {
        this.auth = auth;
        this.search = search;
    }

    @Override
    public String getId() {
        return "spotify";
    }

    @Override
    public String getDisplayName() {
        return "Spotify";
    }

    @Override
    public Set<SourceCapability> getCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public boolean isAuthenticated() {
        return auth.isAuthenticated();
    }

    @Override
    public CompletableFuture<Boolean> authenticate() {
        // Client Credentials — always authenticated, no user action needed
        return CompletableFuture.completedFuture(auth.isAuthenticated());
    }

    @Override
    public void refreshAuthState() {
        // Token auto-refreshes via getAccessToken()
    }

    @Override
    public List<TrackRef> getCachedTracks() {
        return new ArrayList<>();
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
        return CompletableFuture.supplyAsync(() -> {
            List<SourcePlaylist> playlists = new ArrayList<>();
            try {
                String token = auth.getAccessToken();
                if (token == null) return playlists;
                // Featured playlists
                String json = spotifyGet("https://api.spotify.com/v1/browse/featured-playlists?limit=10", token);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonObject pl = root.getAsJsonObject("playlists");
                if (pl != null) {
                    JsonArray items = pl.getAsJsonArray("items");
                    if (items != null) {
                        for (JsonElement e : items) {
                            JsonObject item = e.getAsJsonObject();
                            String id = item.get("id").getAsString();
                            String name = item.get("name").getAsString();
                            String desc = item.has("description") ? item.get("description").getAsString() : "";
                            playlists.add(new SourcePlaylist(id, "spotify", name, 0));
                        }
                    }
                }
            } catch (Exception e) {
                XMusic.LOGGER.debug("[Spotify] Failed to fetch playlists", e);
            }
            return playlists;
        });
    }

    @Override
    public CompletableFuture<List<TrackRef>> getPlaylistTracks(String playlistId) {
        return CompletableFuture.supplyAsync(() -> {
            List<TrackRef> tracks = new ArrayList<>();
            try {
                String token = auth.getAccessToken();
                if (token == null) return tracks;
                String json = spotifyGet("https://api.spotify.com/v1/playlists/" + playlistId + "/tracks?limit=30", token);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonArray items = root.getAsJsonArray("items");
                if (items != null) {
                    for (JsonElement e : items) {
                        try {
                            JsonObject trackObj = e.getAsJsonObject().getAsJsonObject("track");
                            if (trackObj == null || trackObj.has("is_local") && trackObj.get("is_local").getAsBoolean()) continue;
                            String title = trackObj.get("name").getAsString();
                            String artist = "";
                            JsonArray artists = trackObj.getAsJsonArray("artists");
                            if (artists != null && artists.size() > 0) {
                                artist = artists.get(0).getAsJsonObject().get("name").getAsString();
                            }
                            String album = "";
                            String artworkUrl = "";
                            JsonObject albumObj = trackObj.getAsJsonObject("album");
                            if (albumObj != null) {
                                album = albumObj.get("name").getAsString();
                                JsonArray images = albumObj.getAsJsonArray("images");
                                if (images != null && images.size() > 0) {
                                    artworkUrl = images.get(0).getAsJsonObject().get("url").getAsString();
                                }
                            }
                            long durMs = trackObj.has("duration_ms") ? trackObj.get("duration_ms").getAsLong() : 0;
                            String trackId = trackObj.get("id").getAsString();
                            TrackRef ref = new TrackRef.Builder()
                                    .id("spotify:" + trackId)
                                    .sourceId("spotify")
                                    .title(title)
                                    .artist(artist)
                                    .album(album)
                                    .durationMs(durMs)
                                    .artworkUrl(artworkUrl)
                                    .playbackType(com.codexceed.xmusic.source.PlaybackType.REMOTE)
                                    .remoteUri("https://open.spotify.com/track/" + trackId)
                                    .build();
                            tracks.add(ref);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                XMusic.LOGGER.debug("[Spotify] Failed to fetch playlist tracks", e);
            }
            return tracks;
        });
    }

    @Override
    public CompletableFuture<List<TrackRef>> search(String query) {
        return search.search(query);
    }

    public SpotifyAuthService getAuthService() {
        return auth;
    }

    private String spotifyGet(String url, String token) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        try (java.io.InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }
}
