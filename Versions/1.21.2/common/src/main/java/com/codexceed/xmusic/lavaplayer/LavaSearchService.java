package com.codexceed.xmusic.lavaplayer;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.source.PlaybackType;
import com.codexceed.xmusic.source.TrackRef;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Unified LavaPlayer search and URL loading for active sources.
 */
public final class LavaSearchService {
    private static final int MAX_RESULTS = 15;
    private static final long MAX_DURATION_MS = 60 * 60 * 1000L;

    private final LavaPlayerEngine engine;

    public LavaSearchService(LavaPlayerEngine engine) {
        this.engine = engine;
    }

    public CompletableFuture<List<TrackRef>> searchYouTube(String query) {
        return search("ytsearch:" + query);
    }

    public CompletableFuture<List<TrackRef>> searchSoundCloud(String query) {
        return search("scsearch:" + query);
    }

    /** Load a radio/livestream URL directly (HTTP/HTTPS streams, Twitch, etc.). */
    public CompletableFuture<List<TrackRef>> loadStream(String url) {
        return search(url);
    }

    /**
     * Loads YouTube searches/URLs, SoundCloud URLs, HTTP streams, or local file paths.
     */
    public CompletableFuture<List<TrackRef>> search(String uri) {
        CompletableFuture<List<TrackRef>> future = new CompletableFuture<>();

        engine.getManager().loadItem(uri, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                List<TrackRef> results = new ArrayList<>(1);
                if (isAllowedDuration(track)) {
                    results.add(toTrackRef(track, deriveSourceId(uri)));
                }
                XMusic.LOGGER.debug("[LavaSearch] Loaded single track: {}", track.getInfo().title);
                future.complete(results);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                List<TrackRef> results = new ArrayList<>(Math.min(playlist.getTracks().size(), MAX_RESULTS));
                String sourceId = deriveSourceId(uri);
                for (AudioTrack track : playlist.getTracks()) {
                    if (!isAllowedDuration(track)) {
                        continue;
                    }
                    results.add(toTrackRef(track, sourceId));
                    if (results.size() >= MAX_RESULTS) {
                        break;
                    }
                }
                XMusic.LOGGER.debug("[LavaSearch] Playlist/search results: {} tracks from {}",
                        results.size(), uri);
                future.complete(results);
            }

            @Override
            public void noMatches() {
                XMusic.LOGGER.info("[LavaSearch] No matches for: {}", uri);
                future.complete(new ArrayList<>());
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                XMusic.LOGGER.warn("[LavaSearch] Load failed for {}: {}", uri, exception.getMessage());
                future.completeExceptionally(exception);
            }
        });

        return future;
    }

    public void searchYouTube(String query, Consumer<List<TrackRef>> onResult) {
        searchYouTube(query).thenAccept(onResult).exceptionally(error -> {
            XMusic.LOGGER.warn("[LavaSearch] YouTube search failed: {}", error.getMessage());
            onResult.accept(new ArrayList<>());
            return null;
        });
    }

    public static TrackRef toTrackRef(AudioTrack lavaTrack, String sourceId) {
        AudioTrackInfo info = lavaTrack.getInfo();
        String resolvedSourceId = sourceId;
        if (resolvedSourceId == null || resolvedSourceId.isBlank()) {
            resolvedSourceId = deriveSourceId(info.uri != null ? info.uri : "");
        }

        // Resolve artwork URL — LavaPlayer often leaves artworkUrl null,
        // so we construct thumbnails from the video ID for YouTube.
        String artworkUrl = info.artworkUrl;
        if (artworkUrl == null || artworkUrl.isEmpty()) {
            artworkUrl = deriveArtworkUrl(lavaTrack.getIdentifier(), resolvedSourceId);
        }

        return new TrackRef.Builder()
                .id(lavaTrack.getIdentifier())
                .sourceId(resolvedSourceId)
                .title(info.title != null ? info.title : "Unknown")
                .artist(info.author != null ? info.author : "Unknown")
                .album("")
                .durationMs(info.length)
                .artworkUrl(artworkUrl != null ? artworkUrl : "")
                .playbackType(PlaybackType.REMOTE)
                .remoteUri(lavaTrack.getIdentifier())
                .externalUrl(info.uri != null ? info.uri : "")
                .build();
    }

    /** Construct a thumbnail URL from the track identifier and source. */
    private static String deriveArtworkUrl(String identifier, String sourceId) {
        if (identifier == null || identifier.isEmpty()) return "";
        switch (sourceId) {
            case "youtube":
                // YouTube video ID → standard thumbnail
                return "https://img.youtube.com/vi/" + identifier + "/mqdefault.jpg";
            case "soundcloud":
                // SoundCloud doesn't have a simple URL pattern; rely on LavaPlayer
                return "";
            case "bandcamp":
                return "";
            default:
                return "";
        }
    }

    public static String deriveSourceId(String uri) {
        if (uri == null) {
            return "youtube";
        }
        if (uri.startsWith("scsearch:") || uri.contains("soundcloud.com")) {
            return "soundcloud";
        }
        if (uri.startsWith("spsearch:") || uri.contains("open.spotify.com")) {
            return "spotify";
        }
        if (uri.startsWith("ytsearch:") || uri.contains("youtube.com") || uri.contains("youtu.be")) {
            return "youtube";
        }
        if (uri.contains("bandcamp.com")) {
            return "bandcamp";
        }
        if (uri.contains("vimeo.com")) {
            return "vimeo";
        }
        if (uri.contains("twitch.tv")) {
            return "twitch";
        }
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            return "http";
        }
        return "youtube";
    }

    private static boolean isAllowedDuration(AudioTrack track) {
        long length = track.getInfo().length;
        return length <= MAX_DURATION_MS || length == Long.MAX_VALUE;
    }
}
