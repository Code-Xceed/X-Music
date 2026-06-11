package com.codexceed.xmusic.service.youtube;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.config.ConfigManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Resolves YouTube video IDs to direct audio stream URLs using the Piped API.
 *
 * Piped is an open-source YouTube frontend that exposes a JSON API.
 * Given a video ID, the /streams/:id endpoint returns direct audio stream URLs
 * proxied through the Piped instance.
 *
 * Multiple Piped instances are tried as fallback to ensure reliability.
 */
public final class YouTubeAudioResolver {
    private static final long FAILURE_BACKOFF_BASE_MS = 15_000L;
    private static final long FAILURE_BACKOFF_MAX_MS = 180_000L;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Session cache: videoId â†’ resolved audio URL (URLs expire after ~6 hours on YouTube's side). */
    private final Map<String, CachedUrl> urlCache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<ResolvedStream>> inFlight = new ConcurrentHashMap<>();
    private final Map<String, InstanceHealth> instanceHealth = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 4 * 60 * 60 * 1000L; // 4 hours (conservative)

    /** Default Piped API instances (community-hosted). */
    private static final String[] DEFAULT_INSTANCES = {
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://pipedapi.in.projectsegfau.lt"
    };

    /** Public Invidious instances from the official public instances list. */
    private static final String[] DEFAULT_INVIDIOUS_INSTANCES = {
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://inv.thepixora.com",
            "https://yt.chocolatemoo53.com"
    };

    /**
     * Resolve a YouTube video ID to a direct audio stream URL.
     *
     * @param videoId the YouTube video ID (e.g. "dQw4w9WgXcQ")
     * @return a CompletableFuture containing the direct audio URL, or null if resolution fails.
     */
    public CompletableFuture<ResolvedStream> resolve(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        ResolvedStream cached = getCachedResolved(videoId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<ResolvedStream> existing = inFlight.get(videoId);
        if (existing != null) {
            return existing;
        }

        CompletableFuture<ResolvedStream> created = resolveAcrossInstances(videoId)
                .thenCompose(resolved -> resolved != null
                        ? CompletableFuture.completedFuture(resolved)
                        : resolveAcrossInvidiousInstances(videoId))
                .whenComplete((resolved, error) -> {
                    if (resolved != null) {
                        urlCache.put(videoId, new CachedUrl(resolved));
                    }
                    inFlight.remove(videoId);
                });

        CompletableFuture<ResolvedStream> raced = inFlight.putIfAbsent(videoId, created);
        return raced != null ? raced : created;
    }

    public CompletableFuture<ResolvedStream> prefetch(String videoId) {
        return resolve(videoId);
    }

    public ResolvedStream getCachedResolved(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            return null;
        }

        CachedUrl cached = urlCache.get(videoId);
        if (cached == null) {
            return null;
        }
        if (cached.isExpired()) {
            urlCache.remove(videoId);
            return null;
        }
        return cached.stream;
    }

    /**
     * Extract video ID from a YouTube watch URL.
     * Handles formats like:
     * - https://www.youtube.com/watch?v=VIDEO_ID
     * - https://youtu.be/VIDEO_ID
     * - VIDEO_ID (plain)
     */
    public static String extractVideoId(String url) {
        if (url == null || url.isBlank()) return "";

        // Already a plain ID?
        if (!url.contains("/") && !url.contains("?") && url.length() == 11) {
            return url;
        }

        // youtu.be/VIDEO_ID
        if (url.contains("youtu.be/")) {
            int start = url.indexOf("youtu.be/") + 9;
            int end = url.indexOf('?', start);
            return end > 0 ? url.substring(start, end) : url.substring(start);
        }

        // youtube.com/watch?v=VIDEO_ID
        if (url.contains("v=")) {
            int start = url.indexOf("v=") + 2;
            int end = url.indexOf('&', start);
            return end > 0 ? url.substring(start, end) : url.substring(start);
        }

        return url;
    }

    /**
     * Clear the URL cache.
     */
    public void clearCache() {
        urlCache.clear();
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    //  Internal
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private ResolvedStream tryResolve(String instanceBaseUrl, String videoId) throws Exception {
        String endpoint = instanceBaseUrl + "/streams/" + videoId;

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            return null;
        }

        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray audioStreams = root.has("audioStreams") && root.get("audioStreams").isJsonArray()
                ? root.getAsJsonArray("audioStreams")
                : null;
        JsonArray videoStreams = root.has("videoStreams") && root.get("videoStreams").isJsonArray()
                ? root.getAsJsonArray("videoStreams")
                : null;

        JsonObject bestStream = pickBestPipedStream(audioStreams, videoStreams);

        if (bestStream == null) {
            XMusic.LOGGER.warn("No playable native MP4/AAC YouTube stream was available for video {}", videoId);
            return null;
        }

        String url = getString(bestStream, "url");
        String mimeType = getString(bestStream, "mimeType");
        String codec = getString(bestStream, "codec");
        String format = getString(bestStream, "format");
        int bitrate = getInt(bestStream, "bitrate", 0);

        // Also grab metadata for display
        String title = getString(root, "title");
        String uploader = getString(root, "uploader");
        long durationSeconds = root.has("duration") ? root.get("duration").getAsLong() : 0;
        String thumbnailUrl = getString(root, "thumbnailUrl");

        return new ResolvedStream(url, mimeType, codec, format, bitrate,
                title, uploader, durationSeconds * 1000L, thumbnailUrl);
    }

    private String[] getInstances() {
        String configInstances = ConfigManager.get().pipedApiInstances;
        if (configInstances != null && !configInstances.isBlank()) {
            return sanitizeInstances(configInstances.split(","));
        }
        return sanitizeInstances(DEFAULT_INSTANCES);
    }

    private String[] getOrderedInstances() {
        List<String> ordered = java.util.Arrays.stream(getInstances())
                .sorted(Comparator.comparingLong(this::resolverPriority))
                .toList();
        return ordered.toArray(String[]::new);
    }

    private CompletableFuture<ResolvedStream> resolveAcrossInstances(String videoId) {
        String[] instances = getOrderedInstances();
        if (instances.length == 0) {
            XMusic.LOGGER.warn("No YouTube resolver instances are configured.");
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<ResolvedStream> result = new CompletableFuture<>();
        AtomicInteger remaining = new AtomicInteger(instances.length);

        for (String instance : instances) {
            CompletableFuture
                    .supplyAsync(() -> tryResolveCandidate(instance, videoId))
                    .exceptionally(error -> {
                        XMusic.LOGGER.debug("Piped instance {} failed for {}: {}", instance, videoId, error.getMessage());
                        return null;
                    })
                    .thenAccept(candidate -> {
                        if (candidate != null && candidate.stream != null) {
                            markInstanceSuccess(candidate.instance, candidate.durationMs);
                            XMusic.LOGGER.info("Resolved YouTube audio for {} via {} (format: {})",
                                    videoId, candidate.instance, candidate.stream.mimeType);
                            result.complete(candidate.stream);
                            return;
                        }

                        markInstanceFailure(instance);

                        if (remaining.decrementAndGet() == 0 && !result.isDone()) {
                            XMusic.LOGGER.warn("Failed to resolve YouTube audio URL for video: {}", videoId);
                            result.complete(null);
                        }
                    });
        }

        return result;
    }

    private ResolveCandidate tryResolveCandidate(String instanceBaseUrl, String videoId) {
        long startedAt = System.currentTimeMillis();
        try {
            ResolvedStream stream = tryResolve(instanceBaseUrl, videoId);
            return stream == null ? null : new ResolveCandidate(instanceBaseUrl, stream, System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            XMusic.LOGGER.debug("Piped instance {} failed for {}: {}", instanceBaseUrl, videoId, e.getMessage());
            return null;
        }
    }

    private long resolverPriority(String instance) {
        InstanceHealth health = instanceHealth.get(instance);
        if (health == null) {
            return 0L;
        }

        long now = System.currentTimeMillis();
        long cooldownPenalty = Math.max(0L, health.cooldownUntilMs - now);
        long reliabilityPenalty = Math.max(0, health.failureStreak - Math.min(health.successCount, health.failureStreak)) * 10_000L;
        long latencyPenalty = Math.max(0L, health.lastSuccessDurationMs);
        return cooldownPenalty + reliabilityPenalty + latencyPenalty;
    }

    private void markInstanceSuccess(String instance, long durationMs) {
        InstanceHealth health = instanceHealth.computeIfAbsent(instance, ignored -> new InstanceHealth());
        health.failureStreak = 0;
        health.cooldownUntilMs = 0L;
        health.successCount++;
        health.lastSuccessDurationMs = durationMs;
    }

    private void markInstanceFailure(String instance) {
        InstanceHealth health = instanceHealth.computeIfAbsent(instance, ignored -> new InstanceHealth());
        health.failureStreak++;
        long multiplier = 1L << Math.min(health.failureStreak - 1, 3);
        long backoffMs = Math.min(FAILURE_BACKOFF_MAX_MS, FAILURE_BACKOFF_BASE_MS * multiplier);
        health.cooldownUntilMs = System.currentTimeMillis() + backoffMs;
    }

    private String[] sanitizeInstances(String[] rawInstances) {
        Set<String> cleaned = new LinkedHashSet<>();
        for (String instance : rawInstances) {
            if (instance == null) {
                continue;
            }
            String trimmed = instance.trim();
            if (!trimmed.isBlank()) {
                cleaned.add(trimmed);
            }
        }
        return cleaned.toArray(String[]::new);
    }

    private CompletableFuture<ResolvedStream> resolveAcrossInvidiousInstances(String videoId) {
        String[] instances = sanitizeInstances(DEFAULT_INVIDIOUS_INSTANCES);
        if (instances.length == 0) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<ResolvedStream> result = new CompletableFuture<>();
        AtomicInteger remaining = new AtomicInteger(instances.length);

        for (String instance : instances) {
            CompletableFuture
                    .supplyAsync(() -> tryResolveInvidiousCandidate(instance, videoId))
                    .exceptionally(error -> {
                        XMusic.LOGGER.debug("Invidious instance {} failed for {}: {}", instance, videoId, error.getMessage());
                        return null;
                    })
                    .thenAccept(candidate -> {
                        if (candidate != null && candidate.stream != null) {
                            XMusic.LOGGER.info("Resolved YouTube audio for {} via Invidious {} (format: {})",
                                    videoId, candidate.instance, candidate.stream.mimeType);
                            result.complete(candidate.stream);
                            return;
                        }

                        if (remaining.decrementAndGet() == 0 && !result.isDone()) {
                            result.complete(null);
                        }
                    });
        }

        return result;
    }

    private ResolveCandidate tryResolveInvidiousCandidate(String instanceBaseUrl, String videoId) {
        long startedAt = System.currentTimeMillis();
        try {
            ResolvedStream stream = tryResolveInvidious(instanceBaseUrl, videoId);
            return stream == null ? null : new ResolveCandidate(instanceBaseUrl, stream, System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            XMusic.LOGGER.debug("Invidious instance {} failed for {}: {}", instanceBaseUrl, videoId, e.getMessage());
            return null;
        }
    }

    private ResolvedStream tryResolveInvidious(String instanceBaseUrl, String videoId) throws Exception {
        String endpoint = instanceBaseUrl + "/api/v1/videos/" + videoId;

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            return null;
        }

        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray adaptiveFormats = root.has("adaptiveFormats") && root.get("adaptiveFormats").isJsonArray()
                ? root.getAsJsonArray("adaptiveFormats")
                : null;
        JsonArray formatStreams = root.has("formatStreams") && root.get("formatStreams").isJsonArray()
                ? root.getAsJsonArray("formatStreams")
                : null;
        if ((adaptiveFormats == null || adaptiveFormats.isEmpty())
                && (formatStreams == null || formatStreams.isEmpty())) {
            return null;
        }

        JsonObject bestStream = pickBestInvidiousStream(adaptiveFormats, formatStreams);

        if (bestStream == null) {
            return null;
        }

        String title = getString(root, "title");
        String uploader = getString(root, "author");
        long durationMs = parseFlexibleLong(getString(root, "lengthSeconds"), 0L) * 1000L;
        String thumbnailUrl = extractInvidiousThumbnail(root);

        String url = getString(bestStream, "url");
        String type = getString(bestStream, "type");
        String encoding = getString(bestStream, "encoding");
        String container = getString(bestStream, "container");
        int bitrate = parseFlexibleInt(getString(bestStream, "bitrate"), 0);

        return new ResolvedStream(
                url,
                type,
                encoding,
                container,
                bitrate,
                title,
                uploader,
                durationMs,
                thumbnailUrl
        );
    }

    private JsonObject pickBestPipedStream(JsonArray audioStreams, JsonArray videoStreams) {
        JsonObject bestAudio = null;
        int bestAudioScore = Integer.MIN_VALUE;
        if (audioStreams != null) {
            for (JsonElement element : audioStreams) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject stream = element.getAsJsonObject();
                int score = scorePipedAudioStream(stream);
                if (score > bestAudioScore) {
                    bestAudioScore = score;
                    bestAudio = stream;
                }
            }
        }
        if (bestAudio != null) {
            return bestAudio;
        }

        JsonObject bestProgressiveVideo = null;
        int bestVideoScore = Integer.MIN_VALUE;
        if (videoStreams != null) {
            for (JsonElement element : videoStreams) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject stream = element.getAsJsonObject();
                int score = scorePipedProgressiveMp4(stream);
                if (score > bestVideoScore) {
                    bestVideoScore = score;
                    bestProgressiveVideo = stream;
                }
            }
        }
        return bestProgressiveVideo;
    }

    private int scorePipedAudioStream(JsonObject stream) {
        String mimeType = getString(stream, "mimeType");
        String url = getString(stream, "url");
        if (url.isBlank()) {
            return Integer.MIN_VALUE;
        }

        int bitrate = getInt(stream, "bitrate", 0);
        if (mimeType.startsWith("audio/mp4")) {
            return 100_000 + bitrate;
        }
        return Integer.MIN_VALUE;
    }

    private int scorePipedProgressiveMp4(JsonObject stream) {
        String mimeType = getString(stream, "mimeType");
        String url = getString(stream, "url");
        if (url.isBlank()) {
            return Integer.MIN_VALUE;
        }

        boolean videoOnly = getBoolean(stream, "videoOnly", true);
        if (videoOnly || !mimeType.startsWith("video/mp4")) {
            return Integer.MIN_VALUE;
        }

        int bitrate = getInt(stream, "bitrate", 0);
        int height = getInt(stream, "height", 0);
        // Prefer lower-bandwidth progressive MP4 streams because we only need the AAC track.
        return 50_000 - bitrate - (height * 10);
    }

    private JsonObject pickBestInvidiousStream(JsonArray adaptiveFormats, JsonArray formatStreams) {
        JsonObject bestAudio = null;
        int bestAudioScore = Integer.MIN_VALUE;
        if (adaptiveFormats != null) {
            for (JsonElement element : adaptiveFormats) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject stream = element.getAsJsonObject();
                int score = scoreInvidiousAudioStream(stream);
                if (score > bestAudioScore) {
                    bestAudioScore = score;
                    bestAudio = stream;
                }
            }
        }
        if (bestAudio != null) {
            return bestAudio;
        }

        JsonObject bestProgressive = null;
        int bestProgressiveScore = Integer.MIN_VALUE;
        if (formatStreams != null) {
            for (JsonElement element : formatStreams) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject stream = element.getAsJsonObject();
                int score = scoreInvidiousProgressiveMp4(stream);
                if (score > bestProgressiveScore) {
                    bestProgressiveScore = score;
                    bestProgressive = stream;
                }
            }
        }
        return bestProgressive;
    }

    private int scoreInvidiousAudioStream(JsonObject stream) {
        String url = getString(stream, "url");
        if (url.isBlank()) {
            return Integer.MIN_VALUE;
        }

        String type = getString(stream, "type");
        String container = getString(stream, "container");
        String encoding = getString(stream, "encoding");
        int bitrate = parseFlexibleInt(getString(stream, "bitrate"), 0);
        if (type.startsWith("audio/mp4")
                || "mp4".equalsIgnoreCase(container)
                || encoding.startsWith("mp4a")) {
            return 100_000 + bitrate;
        }
        return Integer.MIN_VALUE;
    }

    private int scoreInvidiousProgressiveMp4(JsonObject stream) {
        String url = getString(stream, "url");
        if (url.isBlank()) {
            return Integer.MIN_VALUE;
        }

        String type = getString(stream, "type");
        String container = getString(stream, "container");
        String encoding = getString(stream, "encoding");
        int bitrate = parseFlexibleInt(getString(stream, "bitrate"), 0);
        int height = parseFlexibleInt(getString(stream, "height"), 0);
        if ((type.startsWith("video/mp4") || "mp4".equalsIgnoreCase(container))
                && !encoding.isBlank()
                && encoding.contains("mp4a")) {
            return 50_000 - bitrate - (height * 10);
        }
        return Integer.MIN_VALUE;
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int parseFlexibleInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static long parseFlexibleLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String extractInvidiousThumbnail(JsonObject root) {
        JsonArray thumbnails = root.has("videoThumbnails") && root.get("videoThumbnails").isJsonArray()
                ? root.getAsJsonArray("videoThumbnails")
                : null;
        if (thumbnails == null || thumbnails.isEmpty()) {
            return "";
        }
        JsonElement last = thumbnails.get(thumbnails.size() - 1);
        if (!last.isJsonObject()) {
            return "";
        }
        String url = getString(last.getAsJsonObject(), "url");
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        return url;
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    //  Data classes
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Resolved audio stream information.
     */
    public static final class ResolvedStream {
        public final String url;
        public final String mimeType;
        public final String codec;
        public final String format;
        public final int bitrate;
        public final String title;
        public final String uploader;
        public final long durationMs;
        public final String thumbnailUrl;

        public ResolvedStream(String url, String mimeType, String codec, String format,
                              int bitrate, String title, String uploader, long durationMs,
                              String thumbnailUrl) {
            this.url = url;
            this.mimeType = mimeType;
            this.codec = codec;
            this.format = format;
            this.bitrate = bitrate;
            this.title = title;
            this.uploader = uploader;
            this.durationMs = durationMs;
            this.thumbnailUrl = thumbnailUrl;
        }

        /**
         * @return true if this is an MP4/AAC-family stream that the native AAC decoder can handle.
         */
        public boolean isAac() {
            return mimeType.startsWith("audio/mp4")
                    || mimeType.startsWith("video/mp4")
                    || "MPEG_4".equalsIgnoreCase(format)
                    || "M4A".equalsIgnoreCase(format)
                    || (codec != null && codec.startsWith("mp4a"));
        }
    }

    private static final class CachedUrl {
        final ResolvedStream stream;
        final long timestamp;

        CachedUrl(ResolvedStream stream) {
            this.stream = stream;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    private static final class ResolveCandidate {
        final String instance;
        final ResolvedStream stream;
        final long durationMs;

        private ResolveCandidate(String instance, ResolvedStream stream, long durationMs) {
            this.instance = instance;
            this.stream = stream;
            this.durationMs = durationMs;
        }
    }

    private static final class InstanceHealth {
        volatile int successCount;
        volatile int failureStreak;
        volatile long cooldownUntilMs;
        volatile long lastSuccessDurationMs;
    }
}
