package com.codexceed.xmusic.service.youtube;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.audio.AudioTrack;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.player.TrackRefMapper;
import com.codexceed.xmusic.service.ServiceManager;
import com.codexceed.xmusic.source.TrackRef;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Native YouTube search service.
 * Uses a ranked multi-provider discovery pipeline so normal users do not need API setup.
 * If a developer API key is configured, it can still be used as a final fallback path.
 */
public final class YouTubeService {
    private static final int SEARCH_PREFETCH_LIMIT = 3;
    private static final int SEARCH_RESULT_LIMIT = 24;

    public enum NativePlaybackStatus {
        IDLE,
        SEARCHING,
        DOWNLOADING,
        CONVERTING,
        RESOLVING,
        BUFFERING,
        PLAYING,
        PAUSED,
        ERROR
    }

    private static final String YOUTUBE_RESULTS_URL = "https://www.youtube.com/results?search_query=";
    private static final String YOUTUBE_WATCH_URL = "https://www.youtube.com/watch?v=";
    private static final String DATA_API_BASE = "https://www.googleapis.com/youtube/v3";
    private static final String INNERTUBE_SEARCH_URL =
            "https://www.youtube.com/youtubei/v1/search?key=" + "AIza" + "SyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();
    private static final long SEARCH_CACHE_TTL_MS = 600_000L;

    private record CachedSearch(List<AudioTrack> tracks, long createdAt) {}

    private volatile List<AudioTrack> cachedTracks = Collections.emptyList();
    private volatile boolean searchInFlight;
    private volatile String statusMessage = "Native search and in-game playback ready.";
    private volatile NativePlaybackStatus nativePlaybackStatus = NativePlaybackStatus.IDLE;
    private volatile String nativePlaybackMessage = "Search YouTube and play it in-game.";
    private volatile String lastSearchQuery = "";
    private final YouTubeDownloadManager downloadManager;
    private final YouTubeToolManager toolManager;
    private final Map<String, CachedSearch> searchCache = new ConcurrentHashMap<>();
    private final AtomicInteger searchGeneration = new AtomicInteger();

    public YouTubeService(YouTubeDownloadManager downloadManager, YouTubeToolManager toolManager) {
        this.downloadManager = downloadManager;
        this.toolManager = toolManager;
    }

    public CompletableFuture<List<AudioTrack>> search(String query) {
        String trimmed = query == null ? "" : query.trim();
        lastSearchQuery = trimmed;
        if (trimmed.isEmpty()) {
            searchGeneration.incrementAndGet();
            searchInFlight = false;
            cachedTracks = Collections.emptyList();
            statusMessage = toolManager != null && !toolManager.isReady()
                    ? toolManager.getMessage()
                    : "Native search and in-game playback ready.";
            nativePlaybackStatus = NativePlaybackStatus.IDLE;
            nativePlaybackMessage = toolManager != null && !toolManager.isReady()
                    ? toolManager.getMessage()
                    : "Search YouTube and play it in-game.";
            return CompletableFuture.completedFuture(cachedTracks);
        }

        CachedSearch exactCached = searchCache.get(cacheKey(trimmed));
        if (isCacheFresh(exactCached)) {
            cachedTracks = exactCached.tracks();
            searchInFlight = false;
            statusMessage = "YouTube results ready.";
            nativePlaybackStatus = NativePlaybackStatus.IDLE;
            nativePlaybackMessage = "Pick a result to start native playback.";
            return CompletableFuture.completedFuture(copyTracks(exactCached.tracks()));
        }

        int generation = searchGeneration.incrementAndGet();
        searchInFlight = true;
        statusMessage = "Searching YouTube...";
        nativePlaybackStatus = NativePlaybackStatus.SEARCHING;
        nativePlaybackMessage = "Searching YouTube...";
        return CompletableFuture.supplyAsync(() -> {
            try {
                List<AudioTrack> results = searchInnertube(trimmed);
                if (results.isEmpty()) {
                    results = searchYtDlp(trimmed);
                }
                if (results.isEmpty()) {
                    results = searchPublicWeb(trimmed);
                }
                if (!results.isEmpty()) {
                    List<AudioTrack> ranked = rankAndDedupe(trimmed, results);
                    searchCache.put(cacheKey(trimmed), new CachedSearch(copyTracks(ranked), System.currentTimeMillis()));
                    return completeSearch(generation, trimmed, ranked, false);
                }

                String apiKey = ConfigManager.get().youtubeApiKey;
                if (apiKey != null && !apiKey.isBlank() && !"YOUR_YOUTUBE_API_KEY".equals(apiKey)) {
                    List<AudioTrack> fallbackResults = searchDataApi(trimmed, apiKey);
                    if (!fallbackResults.isEmpty()) {
                        fallbackResults = rankAndDedupe(trimmed, fallbackResults);
                        searchCache.put(cacheKey(trimmed), new CachedSearch(copyTracks(fallbackResults), System.currentTimeMillis()));
                    }
                    return completeSearch(generation, trimmed, fallbackResults, false);
                }
            } catch (Exception e) {
                if (generation == searchGeneration.get()) {
                    statusMessage = "YouTube search failed. Retry in a moment.";
                    nativePlaybackStatus = NativePlaybackStatus.ERROR;
                    nativePlaybackMessage = "Search failed. Retry in a moment.";
                }
                XMusic.LOGGER.error("YouTube search failed", e);
            } finally {
                if (generation == searchGeneration.get()) {
                    searchInFlight = false;
                }
            }

            return completeSearch(generation, trimmed, Collections.emptyList(), true);
        });
    }

    public List<AudioTrack> getCachedTracks() {
        return cachedTracks;
    }

    public boolean isSearchInFlight() {
        return searchInFlight;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public String getLastSearchQuery() {
        return lastSearchQuery;
    }

    public NativePlaybackStatus getNativePlaybackStatus() {
        return nativePlaybackStatus;
    }

    public String getNativePlaybackMessage() {
        return nativePlaybackMessage;
    }

    public YouTubeToolManager.SetupState getToolSetupState() {
        return toolManager != null ? toolManager.getState() : YouTubeToolManager.SetupState.ERROR;
    }

    public String getToolSetupMessage() {
        return toolManager != null ? toolManager.getMessage() : "YouTube tools are unavailable.";
    }

    public boolean hasYtDlp() {
        return toolManager != null && toolManager.hasYtDlp();
    }

    public boolean hasFfmpeg() {
        return toolManager != null && toolManager.hasFfmpeg();
    }

    public boolean isToolInstallInFlight() {
        return toolManager != null && toolManager.isInstalling();
    }

    public boolean areToolsReady() {
        return toolManager != null && toolManager.isReady();
    }

    public YouTubeToolManager.InstallStep getToolInstallStep() {
        return toolManager != null ? toolManager.getInstallStep() : YouTubeToolManager.InstallStep.IDLE;
    }

    public Path getToolBinDirectory() {
        return toolManager != null ? toolManager.getBinDirectory() : null;
    }

    public void refreshToolStatusAsync() {
        if (toolManager != null) {
            toolManager.refreshStatusAsync();
        }
    }

    public CompletableFuture<Boolean> installToolsAsync() {
        if (toolManager == null) {
            return CompletableFuture.completedFuture(false);
        }
        nativePlaybackStatus = NativePlaybackStatus.DOWNLOADING;
        nativePlaybackMessage = "Installing YouTube tools...";
        return toolManager.installToolsAsync().thenApply(success -> {
            if (success) {
                nativePlaybackStatus = NativePlaybackStatus.IDLE;
                nativePlaybackMessage = "YouTube tools installed. Pick a result to start playback.";
            } else {
                nativePlaybackStatus = NativePlaybackStatus.ERROR;
                nativePlaybackMessage = toolManager.getMessage();
            }
            return success;
        });
    }

    public List<AudioTrack> getCachedTracksForQuery(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return copyTracks(cachedTracks);
        }
        CachedSearch exact = searchCache.get(cacheKey(trimmed));
        if (isCacheFresh(exact)) {
            return copyTracks(exact.tracks());
        }
        return filterTracks(cachedTracks, trimmed);
    }

    public void reportNativeResolving(String title) {
        nativePlaybackStatus = NativePlaybackStatus.RESOLVING;
        nativePlaybackMessage = "Resolving native YouTube audio for " + title + "...";
    }

    public void reportNativeDownloading(String title) {
        nativePlaybackStatus = NativePlaybackStatus.DOWNLOADING;
        nativePlaybackMessage = "Downloading " + title + " for local playback...";
    }

    public void reportNativeConverting(String title) {
        nativePlaybackStatus = NativePlaybackStatus.CONVERTING;
        nativePlaybackMessage = "Converting " + title + " into a local playable file...";
    }

    public void reportNativeBuffering(String title) {
        nativePlaybackStatus = NativePlaybackStatus.BUFFERING;
        nativePlaybackMessage = "Buffering native YouTube playback for " + title + "...";
    }

    public void reportNativePlaying(String title) {
        nativePlaybackStatus = NativePlaybackStatus.PLAYING;
        nativePlaybackMessage = "Playing " + title + " natively in-game.";
    }

    public void reportNativePaused(String title) {
        nativePlaybackStatus = NativePlaybackStatus.PAUSED;
        nativePlaybackMessage = "Paused " + title + ".";
    }

    public void reportNativeStopped() {
        nativePlaybackStatus = NativePlaybackStatus.IDLE;
        nativePlaybackMessage = "Search YouTube and play it in-game.";
    }

    public void reportNativeFailure(String message) {
        nativePlaybackStatus = NativePlaybackStatus.ERROR;
        nativePlaybackMessage = message == null || message.isBlank()
                ? "Native YouTube playback failed."
                : message;
    }

    public void prefetchTrack(TrackRef track) {
        if (track == null || downloadManager == null) {
            return;
        }
        downloadManager.prefetch(track);
    }

    private List<AudioTrack> searchInnertube(String query) throws IOException, InterruptedException {
        JsonObject client = new JsonObject();
        client.addProperty("clientName", "WEB");
        client.addProperty("clientVersion", "2.20260427.01.00");
        client.addProperty("hl", "en");
        client.addProperty("gl", "US");

        JsonObject context = new JsonObject();
        context.add("client", client);

        JsonObject body = new JsonObject();
        body.add("context", context);
        body.addProperty("query", query);
        body.addProperty("params", "EgIQAQ==");

        String json = httpPost(INNERTUBE_SEARCH_URL, body.toString(), Duration.ofSeconds(7));
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<JsonObject> renderers = new ArrayList<>();
        collectVideoRenderers(root, renderers, SEARCH_RESULT_LIMIT + 10);
        return tracksFromRenderers(renderers);
    }

    private List<AudioTrack> searchYtDlp(String query) {
        if (toolManager == null || !toolManager.hasYtDlp() || toolManager.getYtDlpExecutable().isBlank()) {
            return Collections.emptyList();
        }

        try {
            Process process = new ProcessBuilder(
                    toolManager.getYtDlpExecutable(),
                    "--dump-json",
                    "--flat-playlist",
                    "--skip-download",
                    "--no-warnings",
                    "--playlist-end",
                    String.valueOf(SEARCH_RESULT_LIMIT),
                    "ytsearch" + SEARCH_RESULT_LIMIT + ":" + query)
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(16, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return Collections.emptyList();
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0 || output.isBlank()) {
                return Collections.emptyList();
            }

            List<AudioTrack> tracks = new ArrayList<>();
            for (String line : output.split("\\R")) {
                if (line == null || line.isBlank() || !line.trim().startsWith("{")) {
                    continue;
                }
                try {
                    JsonObject item = JsonParser.parseString(line).getAsJsonObject();
                    String videoId = getString(item, "id");
                    String title = firstNonBlank(getString(item, "title"), getString(item, "fulltitle"));
                    String channel = firstNonBlank(getString(item, "uploader"), getString(item, "channel"), "YouTube");
                    String thumbnail = firstNonBlank(getString(item, "thumbnail"), "https://i.ytimg.com/vi/" + videoId + "/hqdefault.jpg");
                    long durationMs = parseYtDlpDuration(item);
                    if (!videoId.isBlank() && !title.isBlank()) {
                        tracks.add(buildTrack(videoId, cleanText(title), cleanText(channel), durationMs, thumbnail));
                    }
                } catch (Exception ignored) {
                    // Skip malformed yt-dlp lines; other discovery paths can still provide results.
                }
            }
            return tracks;
        } catch (Exception e) {
            XMusic.LOGGER.warn("yt-dlp YouTube search fallback failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<AudioTrack> searchPublicWeb(String query) throws IOException, InterruptedException {
        String html = httpGet(YOUTUBE_RESULTS_URL + urlEncode(query));
        if (html == null || html.isBlank()) {
            return Collections.emptyList();
        }

        String initialData = extractInitialDataJson(html);
        if (initialData == null || initialData.isBlank()) {
            XMusic.LOGGER.warn("YouTube search page did not contain parsable initial data.");
            return Collections.emptyList();
        }

        JsonObject root = JsonParser.parseString(initialData).getAsJsonObject();
        List<JsonObject> renderers = new ArrayList<>();
        collectVideoRenderers(root, renderers, SEARCH_RESULT_LIMIT + 10);
        return tracksFromRenderers(renderers);
    }

    private List<AudioTrack> tracksFromRenderers(List<JsonObject> renderers) {
        List<AudioTrack> tracks = new ArrayList<>(renderers.size());
        for (JsonObject renderer : renderers) {
            AudioTrack track = toTrack(renderer);
            if (track != null) {
                tracks.add(track);
            }
        }
        return tracks;
    }

    private List<AudioTrack> searchDataApi(String query, String apiKey) throws IOException, InterruptedException {
        String searchUrl = DATA_API_BASE + "/search"
                + "?part=snippet"
                + "&q=" + urlEncode(query)
                + "&type=video"
                + "&videoCategoryId=10"
                + "&maxResults=" + SEARCH_RESULT_LIMIT
                + "&key=" + urlEncode(apiKey);

        String searchJson = httpGet(searchUrl);
        if (searchJson == null || searchJson.isBlank()) {
            return Collections.emptyList();
        }

        JsonObject searchRoot = JsonParser.parseString(searchJson).getAsJsonObject();
        List<String> videoIds = new ArrayList<>();
        JsonArray items = getArray(searchRoot, "items");
        for (JsonElement itemElement : items) {
            if (!itemElement.isJsonObject()) {
                continue;
            }
            JsonObject idObject = getObject(itemElement.getAsJsonObject(), "id");
            String videoId = getString(idObject, "videoId");
            if (!videoId.isBlank()) {
                videoIds.add(videoId);
            }
        }

        if (videoIds.isEmpty()) {
            return Collections.emptyList();
        }

        String detailUrl = DATA_API_BASE + "/videos"
                + "?part=snippet,contentDetails"
                + "&id=" + urlEncode(String.join(",", videoIds))
                + "&key=" + urlEncode(apiKey);

        String detailJson = httpGet(detailUrl);
        if (detailJson == null || detailJson.isBlank()) {
            return Collections.emptyList();
        }

        JsonObject detailRoot = JsonParser.parseString(detailJson).getAsJsonObject();
        JsonArray detailItems = getArray(detailRoot, "items");
        List<AudioTrack> tracks = new ArrayList<>(detailItems.size());
        for (JsonElement itemElement : detailItems) {
            if (!itemElement.isJsonObject()) {
                continue;
            }

            JsonObject item = itemElement.getAsJsonObject();
            JsonObject snippet = getObject(item, "snippet");
            JsonObject contentDetails = getObject(item, "contentDetails");
            String videoId = getString(item, "id");
            String title = getString(snippet, "title");
            String channel = getString(snippet, "channelTitle");
            String thumbnail = extractThumbnail(getObject(snippet, "thumbnails"));
            long durationMs = parseIsoDuration(getString(contentDetails, "duration"));

            if (!videoId.isBlank() && !title.isBlank()) {
                tracks.add(buildTrack(videoId, cleanText(title), cleanText(channel), durationMs, thumbnail));
            }
        }
        return tracks;
    }

    private AudioTrack toTrack(JsonObject renderer) {
        String videoId = getString(renderer, "videoId");
        String title = extractText(renderer.get("title"));
        String artist = extractText(firstPresent(renderer, "longBylineText", "ownerText", "shortBylineText"));
        String durationText = extractText(renderer.get("lengthText"));
        String thumbnail = extractThumbnail(getObject(renderer, "thumbnail"));

        if (videoId.isBlank() || title.isBlank()) {
            return null;
        }

        return buildTrack(
                videoId,
                cleanText(title),
                cleanText(artist.isBlank() ? "YouTube" : artist),
                parseClockDuration(durationText),
                thumbnail);
    }

    private List<AudioTrack> rankAndDedupe(String query, List<AudioTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, AudioTrack> unique = new LinkedHashMap<>();
        for (AudioTrack track : tracks) {
            if (track == null || track.getId() == null || track.getId().isBlank()) {
                continue;
            }
            unique.putIfAbsent(track.getId(), track);
        }

        List<AudioTrack> ranked = new ArrayList<>(unique.values());
        ranked.sort(Comparator.comparingInt((AudioTrack track) -> scoreTrack(query, track)).reversed());
        if (ranked.size() > SEARCH_RESULT_LIMIT) {
            return new ArrayList<>(ranked.subList(0, SEARCH_RESULT_LIMIT));
        }
        return ranked;
    }

    private int scoreTrack(String query, AudioTrack track) {
        String normalizedQuery = normalizeForScore(query);
        String title = normalizeForScore(track.getTitle());
        String artist = normalizeForScore(track.getArtist());
        String combined = title + " " + artist;

        int score = 0;
        if (title.equals(normalizedQuery)) score += 120;
        if (combined.contains(normalizedQuery)) score += 80;
        if (title.startsWith(normalizedQuery)) score += 40;

        Set<String> tokens = queryTokens(normalizedQuery);
        for (String token : tokens) {
            if (title.contains(token)) score += 16;
            if (artist.contains(token)) score += 8;
        }

        if (title.contains("official audio")) score += 22;
        if (title.contains("official video")) score += 12;
        if (artist.contains("topic") || title.contains("provided to youtube")) score += 14;
        if (title.contains("lyrics") || title.contains("lyric")) score += 4;

        if (title.contains("reaction") || title.contains("review") || title.contains("tutorial")) score -= 45;
        if (title.contains("cover") || title.contains("karaoke") || title.contains("instrumental")) score -= 12;
        if (title.contains("live") || title.contains("concert")) score -= 8;
        if (title.contains("shorts") || title.contains("#shorts")) score -= 40;

        long durationMs = track.getDurationMs();
        if (durationMs > 0L) {
            long minutes = durationMs / 60_000L;
            if (minutes >= 2 && minutes <= 8) score += 18;
            if (minutes > 20) score -= 25;
            if (minutes > 60) score -= 50;
            if (durationMs < 45_000L) score -= 20;
        }

        return score;
    }

    private AudioTrack buildTrack(String videoId, String title, String artist, long durationMs, String thumbnailUrl) {
        String watchUrl = YOUTUBE_WATCH_URL + videoId;
        return new AudioTrack.Builder()
                .id("yt:" + videoId)
                .title(title)
                .artist(artist)
                .album("YouTube")
                .durationMs(durationMs)
                .uri(watchUrl)
                .albumArtUrl(thumbnailUrl)
                .source(AudioTrack.Source.YOUTUBE)
                .externalUrl(watchUrl)
                .build();
    }

    private void collectVideoRenderers(JsonElement element, List<JsonObject> sink, int limit) {
        if (element == null || element.isJsonNull() || sink.size() >= limit) {
            return;
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("videoRenderer") && object.get("videoRenderer").isJsonObject()) {
                sink.add(object.getAsJsonObject("videoRenderer"));
                if (sink.size() >= limit) {
                    return;
                }
            }

            for (JsonElement value : object.entrySet().stream().map(java.util.Map.Entry::getValue).toList()) {
                collectVideoRenderers(value, sink, limit);
                if (sink.size() >= limit) {
                    return;
                }
            }
            return;
        }

        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectVideoRenderers(child, sink, limit);
                if (sink.size() >= limit) {
                    return;
                }
            }
        }
    }

    private String extractInitialDataJson(String html) {
        for (String token : new String[]{"var ytInitialData = ", "ytInitialData = ", "window[\"ytInitialData\"] = "}) {
            int tokenIndex = html.indexOf(token);
            if (tokenIndex < 0) {
                continue;
            }

            int jsonStart = html.indexOf('{', tokenIndex + token.length());
            if (jsonStart < 0) {
                continue;
            }

            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (int i = jsonStart; i < html.length(); i++) {
                char ch = html.charAt(i);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (ch == '\\') {
                    escaped = true;
                    continue;
                }
                if (ch == '"') {
                    inString = !inString;
                    continue;
                }
                if (inString) {
                    continue;
                }
                if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                    if (depth == 0) {
                        return html.substring(jsonStart, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private String extractText(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("simpleText")) {
                return object.get("simpleText").getAsString();
            }
            if (object.has("runs") && object.get("runs").isJsonArray()) {
                StringBuilder builder = new StringBuilder();
                for (JsonElement runElement : object.getAsJsonArray("runs")) {
                    if (!runElement.isJsonObject()) {
                        continue;
                    }
                    String text = getString(runElement.getAsJsonObject(), "text");
                    if (!text.isBlank()) {
                        if (!builder.isEmpty()) {
                            builder.append(' ');
                        }
                        builder.append(text);
                    }
                }
                return builder.toString();
            }
        }
        return "";
    }

    private JsonElement firstPresent(JsonObject object, String... keys) {
        if (object == null) {
            return null;
        }
        for (String key : keys) {
            if (object.has(key)) {
                return object.get(key);
            }
        }
        return null;
    }

    private String extractThumbnail(JsonObject thumbnails) {
        JsonArray thumbnailArray = getArray(thumbnails, "thumbnails");
        if (thumbnailArray.isEmpty()) {
            return "";
        }

        JsonElement last = thumbnailArray.get(thumbnailArray.size() - 1);
        if (!last.isJsonObject()) {
            return "";
        }

        String url = getString(last.getAsJsonObject(), "url");
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        return url;
    }

    private long parseClockDuration(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }

        String[] parts = text.trim().split(":");
        long totalSeconds = 0L;
        for (String part : parts) {
            try {
                totalSeconds = (totalSeconds * 60L) + Long.parseLong(part.trim());
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return totalSeconds * 1000L;
    }

    private long parseIsoDuration(String iso) {
        if (iso == null || iso.isBlank() || !iso.startsWith("PT")) {
            return 0L;
        }

        long totalMs = 0L;
        String remaining = iso.substring(2);

        int hoursIndex = remaining.indexOf('H');
        if (hoursIndex >= 0) {
            totalMs += Long.parseLong(remaining.substring(0, hoursIndex)) * 3_600_000L;
            remaining = remaining.substring(hoursIndex + 1);
        }

        int minutesIndex = remaining.indexOf('M');
        if (minutesIndex >= 0) {
            totalMs += Long.parseLong(remaining.substring(0, minutesIndex)) * 60_000L;
            remaining = remaining.substring(minutesIndex + 1);
        }

        int secondsIndex = remaining.indexOf('S');
        if (secondsIndex >= 0) {
            totalMs += Long.parseLong(remaining.substring(0, secondsIndex)) * 1000L;
        }

        return totalMs;
    }

    private long parseYtDlpDuration(JsonObject item) {
        if (item == null || !item.has("duration") || item.get("duration").isJsonNull()) {
            return 0L;
        }
        try {
            return Math.round(item.get("duration").getAsDouble() * 1000.0d);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private String normalizeForScore(String value) {
        if (value == null) {
            return "";
        }
        return cleanText(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private Set<String> queryTokens(String normalizedQuery) {
        Set<String> tokens = new HashSet<>();
        for (String token : normalizedQuery.split(" ")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String httpGet(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(7))
                .header("Accept", "text/html,application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            XMusic.LOGGER.warn("YouTube request returned HTTP {}", response.statusCode());
            return null;
        }
        return response.body();
    }

    private String httpPost(String url, String body, Duration timeout) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Content-Type", "application/json")
                .header("Origin", "https://www.youtube.com")
                .header("Referer", "https://www.youtube.com/")
                .header("User-Agent", "Mozilla/5.0")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            XMusic.LOGGER.warn("YouTube search API returned HTTP {}", response.statusCode());
            return null;
        }
        return response.body();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private JsonObject getObject(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonObject()) {
            return new JsonObject();
        }
        return object.getAsJsonObject(key);
    }

    private JsonArray getArray(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return new JsonArray();
        }
        return object.getAsJsonArray(key);
    }

    private String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void prefetchTopResults(List<AudioTrack> tracks) {
        int limit = Math.min(SEARCH_PREFETCH_LIMIT, tracks.size());
        for (int i = 0; i < limit; i++) {
            AudioTrack track = tracks.get(i);
            if (track == null) {
                continue;
            }
            String videoId = YouTubeAudioResolver.extractVideoId(track.getUri());
            if (!videoId.isBlank()) {
                if (downloadManager != null) {
                    downloadManager.prefetch(new TrackRef.Builder()
                            .id(track.getId())
                            .sourceId("youtube")
                            .title(track.getTitle())
                            .artist(track.getArtist())
                            .album(track.getAlbum())
                            .durationMs(track.getDurationMs())
                            .artworkUrl(track.getAlbumArtUrl())
                            .playbackType(com.codexceed.xmusic.source.PlaybackType.NATIVE)
                            .remoteUri(track.getUri())
                            .externalUrl(track.getExternalUrl())
                            .build());
                }
            }
        }
    }

    private List<AudioTrack> completeSearch(int generation, String query, List<AudioTrack> results, boolean fromEmptyPath) {
        if (generation != searchGeneration.get()) {
            return results;
        }

        cachedTracks = copyTracks(results);
        if (results.isEmpty()) {
            if (!statusMessage.contains("failed")) {
                statusMessage = "No YouTube results found.";
            }
            if (nativePlaybackStatus != NativePlaybackStatus.ERROR) {
                nativePlaybackStatus = NativePlaybackStatus.IDLE;
                nativePlaybackMessage = "No YouTube results found for that query.";
            }
            return cachedTracks;
        }

        statusMessage = "YouTube results ready.";
        nativePlaybackStatus = NativePlaybackStatus.IDLE;
        nativePlaybackMessage = "Pick a result to start native playback.";

        // Pre-resolve ALL search results via Piped API in background.
        // By the time the user picks a track (~2-5s), the URL is already cached.
        preResolveSearchResults(results);

        return cachedTracks;
    }

    /**
     * Pre-resolve all search results via Piped API so any track the user
     * clicks starts playing within seconds from cache.
     */
    private void preResolveSearchResults(List<AudioTrack> tracks) {
        YouTubeStreamResolver resolver = ServiceManager.getYouTubeStreamResolver();
        if (resolver == null) {
            return;
        }

        // Convert AudioTracks to TrackRefs for the resolver
        List<TrackRef> refs = new ArrayList<>();
        for (AudioTrack track : tracks) {
            if (track == null) continue;
            refs.add(new TrackRef.Builder()
                    .id(track.getId())
                    .sourceId("youtube")
                    .title(track.getTitle())
                    .artist(track.getArtist())
                    .album(track.getAlbum())
                    .durationMs(track.getDurationMs())
                    .artworkUrl(track.getAlbumArtUrl())
                    .playbackType(com.codexceed.xmusic.source.PlaybackType.NATIVE)
                    .remoteUri(track.getUri())
                    .externalUrl(track.getExternalUrl())
                    .build());
        }

        resolver.preResolveAllViaPiped(refs);
    }

    private String cacheKey(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isCacheFresh(CachedSearch cached) {
        return cached != null && (System.currentTimeMillis() - cached.createdAt()) <= SEARCH_CACHE_TTL_MS;
    }

    private List<AudioTrack> filterTracks(List<AudioTrack> tracks, String query) {
        if (tracks == null || tracks.isEmpty()) {
            return Collections.emptyList();
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        List<AudioTrack> filtered = new ArrayList<>();
        for (AudioTrack track : tracks) {
            String title = track.getTitle() == null ? "" : track.getTitle().toLowerCase(Locale.ROOT);
            String artist = track.getArtist() == null ? "" : track.getArtist().toLowerCase(Locale.ROOT);
            String album = track.getAlbum() == null ? "" : track.getAlbum().toLowerCase(Locale.ROOT);
            if (title.contains(normalized) || artist.contains(normalized) || album.contains(normalized)) {
                filtered.add(track);
            }
        }
        return filtered;
    }

    private List<AudioTrack> copyTracks(List<AudioTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(tracks);
    }
}
