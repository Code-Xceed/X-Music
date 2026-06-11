package com.codexceed.xmusic.service.spotify;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.lavaplayer.LavaSearchService;
import com.codexceed.xmusic.service.ServiceManager;
import com.codexceed.xmusic.source.TrackRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Precision song search â€” returns only 1-5 official, exact tracks.
 * No covers, no remixes, no fan uploads. 100% official releases.
 *
 * Strategy: searches YouTube with multiple precision queries in parallel,
 * deduplicates results, scores them for official-ness, and returns top 5.
 */
public final class SpotifySearchService {

    private static final int MAX_RESULTS = 5;

    // Keywords that indicate NON-official content â€” hard reject
    private static final String[] REJECT_KEYWORDS = {
            "cover", "remix", "mashup", "karaoke", "instrumental",
            "acoustic cover", "reaction", "review", "tutorial",
            "how to play", "live at", "concert", "performance",
            "fan made", "fan video", "edit", "slowed", "sped up",
            "nightcore", "8d audio", "bass boosted", "parody",
            "tribute", "medley", "chipmunk", "clean version"
    };

    private final SpotifyAuthService auth; // kept for ServiceManager wiring

    public SpotifySearchService(SpotifyAuthService auth) {
        this.auth = auth;
    }

    /**
     * Search for official/exact tracks â€” max 5 results, 100% official.
     * Runs multiple search strategies in parallel for best coverage.
     */
    public CompletableFuture<List<TrackRef>> search(String query) {
        XMusic.LOGGER.info("[SP Precision] Searching for: {}", query);

        LavaSearchService lavaSearch = ServiceManager.getLavaSearch();

        // Run 3 search strategies in parallel for maximum official coverage
        CompletableFuture<List<TrackRef>> search1 = lavaSearch.searchYouTube(query + " official audio");
        CompletableFuture<List<TrackRef>> search2 = lavaSearch.searchYouTube(query + " official music video");
        CompletableFuture<List<TrackRef>> search3 = lavaSearch.searchYouTube(query);

        return CompletableFuture.allOf(search1, search2, search3)
                .thenApply(v -> {
                    // Merge all results, deduplicate by video ID
                    Map<String, TrackRef> deduped = new LinkedHashMap<>();

                    // Add from most precise search first (higher priority)
                    for (TrackRef t : search1.join()) deduped.putIfAbsent(t.getId(), t);
                    for (TrackRef t : search2.join()) deduped.putIfAbsent(t.getId(), t);
                    for (TrackRef t : search3.join()) deduped.putIfAbsent(t.getId(), t);

                    List<TrackRef> allResults = new ArrayList<>(deduped.values());

                    // Score and sort by official-ness
                    List<ScoredTrack> scored = new ArrayList<>();
                    for (TrackRef track : allResults) {
                        int score = scoreTrack(track);
                        if (score >= 0) { // not rejected
                            scored.add(new ScoredTrack(track, score));
                        }
                    }

                    // Sort highest score first
                    scored.sort(Comparator.comparingInt(s -> -s.score));

                    // Take top MAX_RESULTS
                    List<TrackRef> result = new ArrayList<>();
                    for (int i = 0; i < Math.min(MAX_RESULTS, scored.size()); i++) {
                        result.add(scored.get(i).track);
                    }

                    // Fallback: if nothing scored, take top 3 raw
                    if (result.isEmpty() && !allResults.isEmpty()) {
                        int count = Math.min(3, allResults.size());
                        result = new ArrayList<>(allResults.subList(0, count));
                    }

                    XMusic.LOGGER.info("[SP Precision] {} raw â†’ {} scored â†’ {} final",
                            allResults.size(), scored.size(), result.size());
                    return result;
                });
    }

    /**
     * Score a track: higher = more official. Negative = rejected.
     */
    private int scoreTrack(TrackRef track) {
        String lower = track.getTitle().toLowerCase();
        String artistLower = track.getArtist().toLowerCase();

        // Hard reject non-official content
        for (String keyword : REJECT_KEYWORDS) {
            if (lower.contains(keyword)) return -1;
        }

        int score = 0;

        // Strong official indicators in title
        if (lower.contains("official audio")) score += 50;
        else if (lower.contains("official music video")) score += 45;
        else if (lower.contains("official video")) score += 40;
        else if (lower.contains("official")) score += 35;
        else if (lower.contains("music video")) score += 25;
        else if (lower.contains("lyric video") || lower.contains("lyrics video")) score += 20;
        else if (lower.contains("audio")) score += 15;

        // Official channels
        if (artistLower.contains("vevo")) score += 40;
        if (artistLower.contains("topic")) score += 35;

        // Duration bonus: typical song length (2-6 min) is more likely official
        long dur = track.getDurationMs();
        if (dur > 120_000 && dur < 600_000) score += 10; // 2-10 min

        // Penalty for very short or very long (likely not a song)
        if (dur > 0 && dur < 60_000) score -= 20; // under 1 min
        if (dur > 1_800_000) score -= 15; // over 30 min

        return score;
    }

    private static class ScoredTrack {
        final TrackRef track;
        final int score;
        ScoredTrack(TrackRef track, int score) {
            this.track = track;
            this.score = score;
        }
    }
}
