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
import java.util.Collections;
import java.util.Comparator;
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
                List<AudioTrack> tracks = new ArrayList<>(playlist.getTracks());
                if (uri.startsWith("ytsearch:")) {
                    String queryText = uri.substring("ytsearch:".length());
                    tracks.sort((t1, t2) -> Integer.compare(calculateOfficialScore(t2, queryText), calculateOfficialScore(t1, queryText)));
                }
                List<TrackRef> results = new ArrayList<>();
                String sourceId = deriveSourceId(uri);
                for (AudioTrack track : tracks) {
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

    private static int calculateOfficialScore(AudioTrack track, String query) {
        int score = 0;
        String title = track.getInfo().title != null ? track.getInfo().title.toLowerCase() : "";
        String author = track.getInfo().author != null ? track.getInfo().author.toLowerCase() : "";
        String q = query.toLowerCase();

        // 1. Author/Channel checks
        if (author.endsWith("vevo") || author.contains(" vevo") || author.contains("vevo ")) {
            score += 150;
        }
        if (author.endsWith("- topic") || author.contains("- topic") || author.contains(" - topic")) {
            score += 120;
        }

        // 2. Title keywords boosts
        if (title.contains("official audio")) {
            score += 90;
        } else if (title.contains("official video") || title.contains("official music video")) {
            score += 80;
        } else if (title.contains("official lyric") || title.contains("official lyrics")) {
            score += 50;
        } else if (title.contains("lyrics") || title.contains("lyric")) {
            score += 25;
        }

        // 3. Keyword penalties (only if the query itself doesn't contain these words)
        if (title.contains("cover") && !q.contains("cover")) {
            score -= 100;
        }
        if (title.contains("reaction") && !q.contains("reaction")) {
            score -= 120;
        }
        if ((title.contains("karaoke") || title.contains("instrumental") || title.contains("backing track")) 
                && !q.contains("karaoke") && !q.contains("instrumental")) {
            score -= 90;
        }
        if ((title.contains("1 hour") || title.contains("10 hour") || title.contains("loop") || title.contains("infinite")) 
                && !q.contains("hour") && !q.contains("loop")) {
            score -= 110;
        }
        if ((title.contains("slowed") || title.contains("reverb") || title.contains("sped up") || title.contains("speed up") || title.contains("bass boosted") || title.contains("remix")) 
                && !q.contains("slowed") && !q.contains("reverb") && !q.contains("sped") && !q.contains("speed") && !q.contains("boosted") && !q.contains("remix")) {
            score -= 80;
        }
        if (title.contains("live") && !q.contains("live")) {
            score -= 40;
        }
        if (title.contains("shorts") || title.contains("#shorts")) {
            score -= 50;
        }

        // 4. Query word matching overlap
        String[] words = q.replaceAll("[^a-zA-Z0-9\\s]", " ").split("\\s+");
        int matched = 0;
        int validWords = 0;
        for (String w : words) {
            String wt = w.trim();
            if (wt.length() >= 2) {
                validWords++;
                if (title.contains(wt) || author.contains(wt)) {
                    matched++;
                }
            }
        }
        if (validWords > 0) {
            score += (int) (((double) matched / validWords) * 70);
        }

        // 5. Duration checks (prefer standard song lengths of 2-8 minutes if possible)
        long lengthMs = track.getInfo().length;
        if (lengthMs > 0) {
            long minutes = lengthMs / 60_000L;
            if (minutes >= 2 && minutes <= 8) {
                score += 20;
            }
            if (minutes > 15) {
                score -= 30;
            }
            if (minutes > 30) {
                score -= 60;
            }
        }

        return score;
    }
}
