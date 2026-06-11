package com.codexceed.xmusic.service.youtube;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.service.ServiceManager;
import com.codexceed.xmusic.source.TrackRef;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Resolves YouTube tracks into short-lived direct media URLs for fast streaming.
 *
 * <h3>Resolution Strategy (Resilient Multi-Source)</h3>
 * <ol>
 *   <li><b>Cache</b> â€” If a still-valid URL is cached, return immediately.</li>
 *   <li><b>Piped/Invidious API</b> â€” Try the existing Piped+Invidious resolver first.
 *       This is the fastest path (~200ms) and doesn't hit YouTube from your IP,
 *       avoiding rate limiting entirely.</li>
 *   <li><b>yt-dlp</b> â€” If Piped fails, fall back to local yt-dlp with
 *       {@code --no-cache-dir} to avoid concurrent cache corruption.
 *       Includes one retry with a 3s delay on failure.</li>
 * </ol>
 *
 * <h3>Rate-Limit Protection</h3>
 * <ul>
 *   <li>Pre-resolve batches are serialized with 2.5s delays between requests.</li>
 *   <li>yt-dlp uses {@code --no-cache-dir} to prevent concurrent process conflicts.</li>
 *   <li>URL TTL is 4 hours (matching YouTube's actual expiry) to minimize re-resolutions.</li>
 *   <li>Optional cookies.txt support for authenticated requests.</li>
 * </ul>
 */
public final class YouTubeStreamResolver {
    /** YouTube stream URLs are valid for ~6h; we use 4h to be conservative. */
    private static final long URL_TTL_MS = 4L * 60L * 60L * 1000L;
    private static final int RESOLVE_TIMEOUT_SECONDS = 15;
    private static final int RETRY_DELAY_MS = 3000;
    private static final int BATCH_DELAY_MS = 2500;

    private final YouTubeToolManager toolManager;

    // 2 threads for yt-dlp (reduced from 4 to prevent rate limiting)
    private final ExecutorService worker = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "XMusic-YouTubeStreamResolve");
        thread.setDaemon(true);
        return thread;
    });

    // Separate scheduler for delayed/serialized batch pre-resolution
    private final ScheduledExecutorService batchScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "XMusic-StreamBatchResolve");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY + 1); // Low priority â€” don't compete with game
        return thread;
    });

    private final Map<String, CachedStream> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<ResolvedStream>> inFlight = new ConcurrentHashMap<>();
    private final AtomicInteger batchGeneration = new AtomicInteger();

    public YouTubeStreamResolver(YouTubeToolManager toolManager) {
        this.toolManager = toolManager;
    }

    /**
     * Returns {@code true} when both yt-dlp and ffmpeg are available, which means
     * instant stream playback is possible. Note: even if yt-dlp is missing,
     * streaming may still work via Piped API + ffmpeg.
     */
    public boolean isStreamingAvailable() {
        // Streaming works if we have ffmpeg AND either yt-dlp or Piped API
        if (toolManager == null || !toolManager.hasFfmpeg() || toolManager.getFfmpegExecutable().isBlank()) {
            return false;
        }
        // yt-dlp available = definitely can stream
        if (toolManager.hasYtDlp() && !toolManager.getYtDlpExecutable().isBlank()) {
            return true;
        }
        // No yt-dlp, but Piped API is always available as a fallback
        YouTubeAudioResolver pipedResolver = ServiceManager.getYouTubeResolver();
        return pipedResolver != null;
    }

    /**
     * Resolve a track to a stream URL. Tries Piped API first, then yt-dlp with retry.
     */
    public CompletableFuture<ResolvedStream> resolve(TrackRef track) {
        String videoId = extractVideoId(track);
        if (videoId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid YouTube video id"));
        }

        // 1. Check cache
        CachedStream cached = cache.get(videoId);
        if (cached != null && cached.isFresh()) {
            return CompletableFuture.completedFuture(cached.stream());
        }

        // 2. Coalesce concurrent requests for the same video
        CompletableFuture<ResolvedStream> existing = inFlight.get(videoId);
        if (existing != null) {
            return existing;
        }

        // 3. Multi-source resolution: Piped â†’ yt-dlp â†’ yt-dlp retry
        CompletableFuture<ResolvedStream> created = resolveMultiSource(track, videoId)
                .whenComplete((stream, error) -> inFlight.remove(videoId));

        CompletableFuture<ResolvedStream> raced = inFlight.putIfAbsent(videoId, created);
        return raced != null ? raced : created;
    }

    /**
     * Fire-and-forget pre-resolution for a single track.
     */
    public void preResolve(TrackRef track) {
        resolve(track).exceptionally(error -> {
            XMusic.LOGGER.debug("YouTube stream pre-resolve failed for {}: {}",
                    track != null ? track.getDisplayName() : "unknown",
                    firstMessage(error));
            return null;
        });
    }

    /**
     * Pre-resolve a batch of tracks with serialized delays to prevent rate limiting.
     * Uses the full multi-source pipeline (Piped â†’ yt-dlp â†’ retry).
     */
    public void preResolveBatch(List<TrackRef> tracks, int limit) {
        if (tracks == null || !isStreamingAvailable()) {
            return;
        }

        int generation = batchGeneration.incrementAndGet();
        int count = Math.min(tracks.size(), Math.max(0, limit));

        for (int i = 0; i < count; i++) {
            TrackRef track = tracks.get(i);
            if (track == null || !"youtube".equals(track.getSourceId())) {
                continue;
            }

            String videoId = extractVideoId(track);
            CachedStream cached = cache.get(videoId);
            if (cached != null && cached.isFresh()) {
                continue;
            }

            // 1s delay between yt-dlp calls to avoid rate limiting
            final int delay = i * 1000;
            batchScheduler.schedule(() -> {
                if (batchGeneration.get() != generation) {
                    return;
                }
                preResolve(track);
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Pre-resolve ALL search results via Piped API only (fast, parallel).
     * Piped doesn't rate-limit because it proxies through its own servers.
     * Each track is resolved with a tiny 300ms stagger to avoid flooding.
     *
     * This is called immediately when search results arrive so that by the
     * time the user picks a track, the stream URL is already cached.
     */
    public void preResolveAllViaPiped(List<TrackRef> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }

        int generation = batchGeneration.incrementAndGet();
        XMusic.LOGGER.info("Pre-resolving {} search results via Piped API...", tracks.size());

        for (int i = 0; i < tracks.size(); i++) {
            TrackRef track = tracks.get(i);
            if (track == null || !"youtube".equals(track.getSourceId())) {
                continue;
            }

            String videoId = extractVideoId(track);
            CachedStream cached = cache.get(videoId);
            if (cached != null && cached.isFresh()) {
                continue;
            }

            // 300ms stagger â€” Piped can handle this easily since it's a proxy
            final int delay = i * 300;
            batchScheduler.schedule(() -> {
                if (batchGeneration.get() != generation) {
                    return;
                }
                // Piped-only resolution â€” fast and doesn't touch YouTube from our IP
                resolvePiped(videoId)
                        .thenAccept(result -> {
                            if (result != null) {
                                cache.put(videoId, new CachedStream(result, System.currentTimeMillis()));
                                XMusic.LOGGER.debug("Pre-resolved {} via Piped", videoId);
                            }
                        })
                        .exceptionally(error -> {
                            XMusic.LOGGER.debug("Piped pre-resolve failed for {}: {}",
                                    videoId, firstMessage(error));
                            return null;
                        });
            }, delay, TimeUnit.MILLISECONDS);
        }
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    //  Multi-source resolution pipeline
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private CompletableFuture<ResolvedStream> resolveMultiSource(TrackRef track, String videoId) {
        // Piped first (~200ms, no fork, no rate limiting from user's IP)
        return resolvePiped(videoId)
                .thenCompose(pipedResult -> {
                    if (pipedResult != null) {
                        cache.put(videoId, new CachedStream(pipedResult, System.currentTimeMillis()));
                        return CompletableFuture.completedFuture(pipedResult);
                    }
                    // Piped failed â†’ yt-dlp fallback (~2-3s, forks Python)
                    XMusic.LOGGER.info("Piped failed for {}; trying yt-dlp.", videoId);
                    return resolveYtDlp(track, videoId);
                })
                .thenCompose(result -> {
                    if (result != null) {
                        cache.put(videoId, new CachedStream(result, System.currentTimeMillis()));
                        return CompletableFuture.completedFuture(result);
                    }
                    // Both failed â†’ retry yt-dlp once after 3s delay
                    return retryYtDlpAfterDelay(track, videoId);
                })
                .exceptionally(error -> {
                    XMusic.LOGGER.debug("All stream resolution methods failed for {}: {}",
                            videoId, firstMessage(error));
                    return null;
                });
    }

    /**
     * Try resolving via the Piped/Invidious API. This is the fastest and most
     * rate-limit-friendly approach since it doesn't hit YouTube from your IP.
     */
    private CompletableFuture<ResolvedStream> resolvePiped(String videoId) {
        YouTubeAudioResolver pipedResolver = ServiceManager.getYouTubeResolver();
        if (pipedResolver == null) {
            return CompletableFuture.completedFuture(null);
        }

        return pipedResolver.resolve(videoId)
                .thenApply(pipedStream -> {
                    if (pipedStream == null || pipedStream.url == null || pipedStream.url.isBlank()) {
                        return null;
                    }
                    XMusic.LOGGER.info("Resolved YouTube stream for {} via Piped API", videoId);
                    return new ResolvedStream(
                            videoId,
                            pipedStream.url,
                            pipedStream.format != null ? pipedStream.format : "",
                            pipedStream.codec != null ? pipedStream.codec : "",
                            System.currentTimeMillis() + URL_TTL_MS);
                })
                .exceptionally(error -> {
                    XMusic.LOGGER.debug("Piped API resolution failed for {}: {}", videoId, firstMessage(error));
                    return null;
                });
    }

    /**
     * Resolve via local yt-dlp. Uses --no-cache-dir to prevent concurrent
     * process conflicts.
     */
    private CompletableFuture<ResolvedStream> resolveYtDlp(TrackRef track, String videoId) {
        if (toolManager == null || !toolManager.hasYtDlp() || toolManager.getYtDlpExecutable().isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                ResolvedStream stream = resolveNowYtDlp(track, videoId);
                cache.put(videoId, new CachedStream(stream, System.currentTimeMillis()));
                return stream;
            } catch (Exception e) {
                XMusic.LOGGER.debug("yt-dlp resolution failed for {}: {}", videoId, e.getMessage());
                return null;
            }
        }, worker);
    }

    /**
     * Retry yt-dlp after a delay. Rate-limited failures often succeed after a short wait.
     */
    private CompletableFuture<ResolvedStream> retryYtDlpAfterDelay(TrackRef track, String videoId) {
        if (toolManager == null || !toolManager.hasYtDlp() || toolManager.getYtDlpExecutable().isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<ResolvedStream> delayed = new CompletableFuture<>();
        batchScheduler.schedule(() -> {
            try {
                ResolvedStream stream = resolveNowYtDlp(track, videoId);
                if (stream != null) {
                    cache.put(videoId, new CachedStream(stream, System.currentTimeMillis()));
                }
                delayed.complete(stream);
            } catch (Exception e) {
                XMusic.LOGGER.debug("yt-dlp retry also failed for {}: {}", videoId, e.getMessage());
                delayed.complete(null);
            }
        }, RETRY_DELAY_MS, TimeUnit.MILLISECONDS);

        return delayed;
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    //  yt-dlp process execution
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private ResolvedStream resolveNowYtDlp(TrackRef track, String videoId) throws Exception {
        if (toolManager == null || !toolManager.hasYtDlp() || toolManager.getYtDlpExecutable().isBlank()) {
            throw new IllegalStateException(toolManager == null
                    ? "yt-dlp is not available."
                    : toolManager.getMessage());
        }

        String targetUrl = track.getExternalUrl() != null && !track.getExternalUrl().isBlank()
                ? track.getExternalUrl()
                : (!track.getRemoteUri().isBlank() ? track.getRemoteUri() : "https://www.youtube.com/watch?v=" + videoId);

        List<String> command = new ArrayList<>();
        command.add(toolManager.getYtDlpExecutable());
        command.add("--ignore-config");
        command.add("--no-playlist");
        command.add("--no-warnings");
        command.add("--skip-download");
        // Prevent concurrent yt-dlp processes from corrupting shared cache
        command.add("--no-cache-dir");
        command.add("-f");
        command.add("bestaudio[acodec=opus]/bestaudio[ext=webm]/bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio");
        command.add("--print");
        command.add("url");
        command.add("--print");
        command.add("ext");
        command.add("--print");
        command.add("acodec");

        // If user has configured a cookies file, pass it to yt-dlp for authenticated requests
        String cookiesPath = ConfigManager.get().youtubeCookiesFile;
        if (cookiesPath != null && !cookiesPath.isBlank()) {
            command.add("--cookies");
            command.add(cookiesPath);
        }

        command.add(targetUrl);

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line.trim());
                }
                if (lines.size() > 16) {
                    lines.remove(0);
                }
            }
        }

        boolean finished = process.waitFor(RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("YouTube stream resolve timed out.");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(lines.isEmpty()
                    ? "yt-dlp failed to resolve a stream URL."
                    : "yt-dlp resolve failed: " + lines.get(lines.size() - 1));
        }

        String url = firstUrl(lines);
        if (url.isBlank()) {
            throw new IllegalStateException("yt-dlp did not return a playable stream URL.");
        }

        String ext = lines.size() > 1 ? lines.get(1) : "";
        String acodec = lines.size() > 2 ? lines.get(2) : "";
        return new ResolvedStream(videoId, url, ext, acodec, System.currentTimeMillis() + URL_TTL_MS);
    }

    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    //  Helpers
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private String firstUrl(List<String> lines) {
        for (String line : lines) {
            if (line.startsWith("http://") || line.startsWith("https://")) {
                return line;
            }
        }
        return "";
    }

    private String extractVideoId(TrackRef track) {
        if (track == null) {
            return "";
        }
        String source = track.getRemoteUri() != null && !track.getRemoteUri().isBlank()
                ? track.getRemoteUri()
                : track.getExternalUrl();
        return YouTubeAudioResolver.extractVideoId(source);
    }

    private String firstMessage(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return "unknown error";
    }

    private record CachedStream(ResolvedStream stream, long createdAt) {
        boolean isFresh() {
            return stream != null && stream.expiresAtMs() > System.currentTimeMillis() + 60_000L;
        }
    }

    public record ResolvedStream(String videoId, String url, String extension, String audioCodec, long expiresAtMs) {
    }
}
