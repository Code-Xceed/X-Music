package com.codexceed.xmusic.service.youtube;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.config.XMusicConfig;
import com.codexceed.xmusic.source.TrackRef;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Downloads YouTube tracks via yt-dlp and prefers a native playable AAC/M4A
 * cache file first. If YouTube does not expose a usable AAC/M4A result, it
 * falls back to ffmpeg-backed MP3 conversion.
 */
public final class YouTubeDownloadManager {
    private static final String PRIMARY_OUTPUT_EXTENSION = ".m4a";
    private static final String FALLBACK_OUTPUT_EXTENSION = ".mp3";
    private final YouTubeToolManager toolManager;

    private final ExecutorService worker = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "XMusic-YouTubeDownload");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, CompletableFuture<PreparedTrack>> inFlight = new ConcurrentHashMap<>();

    public YouTubeDownloadManager(YouTubeToolManager toolManager) {
        this.toolManager = toolManager;
    }

    public CompletableFuture<PreparedTrack> prepareForPlayback(TrackRef track) {
        return schedule(track, false);
    }

    public CompletableFuture<PreparedTrack> prefetch(TrackRef track) {
        return schedule(track, true);
    }

    public boolean isCached(TrackRef track) {
        String videoId = extractVideoId(track);
        return !videoId.isBlank() && locateCachedFile(videoId) != null;
    }

    public Path getCacheDirectory() {
        XMusicConfig config = ConfigManager.get();
        if (config.youtubeCacheDirectory != null && !config.youtubeCacheDirectory.isBlank()) {
            return Paths.get(config.youtubeCacheDirectory);
        }
        return XMusic.getPlatform().getGameDir().resolve("xmusic").resolve("cache").resolve("youtube");
    }

    public void cacheInBackground(TrackRef track) {
        prefetch(track).exceptionally(error -> {
            XMusic.LOGGER.debug(
                    "Background YouTube cache failed for {}: {}",
                    track != null ? track.getDisplayName() : "unknown",
                    firstMessage(error));
            return null;
        });
    }

    public Path getFavoritesDirectory() {
        XMusicConfig config = ConfigManager.get();
        if (config.youtubeFavoritesDirectory != null && !config.youtubeFavoritesDirectory.isBlank()) {
            return Paths.get(config.youtubeFavoritesDirectory);
        }
        return XMusic.getPlatform().getGameDir().resolve("xmusic").resolve("library").resolve("youtube");
    }

    private CompletableFuture<PreparedTrack> schedule(TrackRef track, boolean prefetch) {
        if (track == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Track is required"));
        }

        String videoId = extractVideoId(track);
        if (videoId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid YouTube video id"));
        }

        Path cachedFile = locateCachedFile(videoId);
        if (cachedFile != null) {
            return CompletableFuture.completedFuture(new PreparedTrack(videoId, cachedFile, true, false));
        }

        CompletableFuture<PreparedTrack> existing = inFlight.get(videoId);
        if (existing != null) {
            return existing;
        }

        CompletableFuture<PreparedTrack> created = CompletableFuture.supplyAsync(() -> {
            try {
                return prepareLocalTrack(track, videoId, prefetch);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, worker).whenComplete((prepared, error) -> inFlight.remove(videoId));

        CompletableFuture<PreparedTrack> raced = inFlight.putIfAbsent(videoId, created);
        return raced != null ? raced : created;
    }

    private PreparedTrack prepareLocalTrack(TrackRef track, String videoId, boolean prefetch) throws Exception {
        ensureDirectories();

        if (toolManager == null || !toolManager.isReady()) {
            throw new IllegalStateException(toolManager == null
                    ? "YouTube tools are not available."
                    : toolManager.getMessage());
        }

        Path outputFile = locateCachedFile(videoId);
        if (outputFile != null) {
            return new PreparedTrack(videoId, outputFile, true, false);
        }

        Path nativeOutputFile = getPrimaryCachedFile(videoId);
        DownloadAttempt nativeAttempt = runYtDlp(track, buildNativeDownloadCommand(track, videoId, nativeOutputFile), prefetch);
        Path preparedNative = locatePreparedOutput(nativeOutputFile, nativeAttempt.outputLines());
        if (nativeAttempt.exitCode() == 0 && preparedNative != null) {
            enforceCachePolicy(preparedNative);
            return new PreparedTrack(videoId, preparedNative, false, false);
        }

        String ffmpeg = resolveFfmpegExecutable();
        if (ffmpeg.isBlank()) {
            throw new IllegalStateException(buildFailureMessage(nativeAttempt.exitCode(), nativeAttempt.outputLines()));
        }

        Path fallbackOutputFile = getFallbackCachedFile(videoId);
        DownloadAttempt fallbackAttempt = runYtDlp(track, buildFallbackDownloadCommand(track, videoId, fallbackOutputFile), prefetch);
        Path preparedFallback = locatePreparedOutput(fallbackOutputFile, fallbackAttempt.outputLines());
        if (fallbackAttempt.exitCode() == 0 && preparedFallback != null) {
            enforceCachePolicy(preparedFallback);
            return new PreparedTrack(videoId, preparedFallback, false, true);
        }

        throw new IllegalStateException(buildFailureMessage(
                fallbackAttempt.exitCode() != 0 ? fallbackAttempt.exitCode() : nativeAttempt.exitCode(),
                !fallbackAttempt.outputLines().isEmpty() ? fallbackAttempt.outputLines() : nativeAttempt.outputLines()));
    }

    private List<String> buildNativeDownloadCommand(TrackRef track, String videoId, Path outputFile) {
        XMusicConfig config = ConfigManager.get();
        String ytDlp = resolveYtDlpExecutable();
        String url = track.getExternalUrl() != null && !track.getExternalUrl().isBlank()
                ? track.getExternalUrl()
                : (!track.getRemoteUri().isBlank() ? track.getRemoteUri() : "https://www.youtube.com/watch?v=" + videoId);

        List<String> command = new ArrayList<>();
        command.add(ytDlp);
        command.add("--ignore-config");
        command.add("--no-playlist");
        command.add("--force-overwrites");
        command.add("--newline");
        command.add("--no-warnings");
        command.add("--no-cache-dir");
        command.add("--restrict-filenames");
        command.add("--concurrent-fragments");
        command.add(Integer.toString(Math.max(1, config.youtubeDownloadConcurrentFragments)));
        if (config.youtubeCookiesFile != null && !config.youtubeCookiesFile.isBlank()) {
            command.add("--cookies");
            command.add(config.youtubeCookiesFile);
        }
        command.add("-f");
        command.add("bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]");
        command.add("--output");
        command.add(stripExtension(outputFile).toString() + ".%(ext)s");
        command.add("--print");
        command.add("after_move:filepath");
        command.add(url);
        return command;
    }

    private List<String> buildFallbackDownloadCommand(TrackRef track, String videoId, Path outputFile) {
        XMusicConfig config = ConfigManager.get();
        String ytDlp = resolveYtDlpExecutable();
        String ffmpeg = resolveFfmpegExecutable();
        String url = track.getExternalUrl() != null && !track.getExternalUrl().isBlank()
                ? track.getExternalUrl()
                : (!track.getRemoteUri().isBlank() ? track.getRemoteUri() : "https://www.youtube.com/watch?v=" + videoId);

        List<String> command = new ArrayList<>();
        command.add(ytDlp);
        command.add("--ignore-config");
        command.add("--no-playlist");
        command.add("--force-overwrites");
        command.add("--newline");
        command.add("--no-warnings");
        command.add("--no-cache-dir");
        command.add("--restrict-filenames");
        command.add("--concurrent-fragments");
        command.add(Integer.toString(Math.max(1, config.youtubeDownloadConcurrentFragments)));
        if (config.youtubeCookiesFile != null && !config.youtubeCookiesFile.isBlank()) {
            command.add("--cookies");
            command.add(config.youtubeCookiesFile);
        }
        command.add("-f");
        command.add("bestaudio");
        command.add("--extract-audio");
        command.add("--audio-format");
        command.add("mp3");
        command.add("--audio-quality");
        command.add("2");
        if (!ffmpeg.isBlank()) {
            command.add("--ffmpeg-location");
            command.add(ffmpeg);
        }
        command.add("--output");
        command.add(stripExtension(outputFile).toString() + ".%(ext)s");
        command.add("--print");
        command.add("after_move:filepath");
        command.add(url);
        return command;
    }

    private void ensureDirectories() throws IOException {
        Files.createDirectories(getCacheDirectory());
        Files.createDirectories(getFavoritesDirectory());
    }

    private void enforceCachePolicy(Path newestFile) {
        XMusicConfig config = ConfigManager.get();
        long maxBytes = Math.max(64L, config.youtubeCacheMaxSizeMb) * 1024L * 1024L;
        int maxTracks = Math.max(4, config.youtubeCacheMaxTracks);

        try {
            List<Path> files = Files.list(getCacheDirectory())
                    .filter(path -> isManagedCacheFile(path))
                    .sorted(Comparator.comparingLong(this::safeLastModified))
                    .toList();

            long totalBytes = 0L;
            for (Path path : files) {
                totalBytes += safeSize(path);
            }

            int index = 0;
            while ((files.size() - index) > maxTracks || totalBytes > maxBytes) {
                Path candidate = files.get(index++);
                if (candidate.equals(newestFile)) {
                    continue;
                }
                long size = safeSize(candidate);
                Files.deleteIfExists(candidate);
                totalBytes -= size;
            }
        } catch (Exception e) {
            XMusic.LOGGER.warn("Failed to enforce YouTube cache policy", e);
        }
    }

    private long safeLastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
            return Long.MAX_VALUE;
        }
    }

    private long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private Path tryFindPrintedOutput(List<String> outputLines) {
        for (int i = outputLines.size() - 1; i >= 0; i--) {
            String line = outputLines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }
            try {
                Path path = Paths.get(line.trim());
                if (Files.isRegularFile(path)) {
                    return path;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String buildFailureMessage(int exitCode, List<String> outputLines) {
        String tail = outputLines.isEmpty() ? "" : outputLines.get(outputLines.size() - 1);
        if (tail == null || tail.isBlank()) {
            return "yt-dlp failed with exit code " + exitCode;
        }
        return "yt-dlp failed: " + tail;
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

    private String resolveYtDlpExecutable() {
        if (toolManager != null && toolManager.getYtDlpExecutable() != null && !toolManager.getYtDlpExecutable().isBlank()) {
            return toolManager.getYtDlpExecutable();
        }
        XMusicConfig config = ConfigManager.get();
        return isWindows() && config.youtubeYtDlpPath != null && !config.youtubeYtDlpPath.isBlank()
                ? config.youtubeYtDlpPath
                : (isWindows() ? "yt-dlp.exe" : "yt-dlp");
    }

    private String resolveFfmpegExecutable() {
        if (toolManager != null && toolManager.getFfmpegExecutable() != null && !toolManager.getFfmpegExecutable().isBlank()) {
            return toolManager.getFfmpegExecutable();
        }
        XMusicConfig config = ConfigManager.get();
        return isWindows() && config.youtubeFfmpegPath != null && !config.youtubeFfmpegPath.isBlank()
                ? config.youtubeFfmpegPath
                : (isWindows() ? "ffmpeg.exe" : "ffmpeg");
    }

    private Path getCachedFile(String videoId) {
        return getPrimaryCachedFile(videoId);
    }

    private Path getPrimaryCachedFile(String videoId) {
        return getCacheDirectory().resolve(videoId + PRIMARY_OUTPUT_EXTENSION);
    }

    private Path getFallbackCachedFile(String videoId) {
        return getCacheDirectory().resolve(videoId + FALLBACK_OUTPUT_EXTENSION);
    }

    private Path locateCachedFile(String videoId) {
        Path primary = getPrimaryCachedFile(videoId);
        if (Files.isRegularFile(primary)) {
            return primary;
        }
        Path fallback = getFallbackCachedFile(videoId);
        if (Files.isRegularFile(fallback)) {
            return fallback;
        }
        return null;
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

    private Path stripExtension(Path outputFile) {
        String fileName = outputFile.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        return outputFile.getParent().resolve(stem);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private DownloadAttempt runYtDlp(TrackRef track, List<String> command, boolean prefetch) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);

        XMusic.LOGGER.info("Starting YouTube {} job for {} via yt-dlp", prefetch ? "prefetch" : "playback", track.getDisplayName());
        Process process = builder.start();

        List<String> outputLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.add(line);
                if (outputLines.size() > 80) {
                    outputLines.remove(0);
                }
                XMusic.LOGGER.debug("yt-dlp[{}]: {}", extractVideoId(track), line);
            }
        }

        int timeoutSeconds = Math.max(30, ConfigManager.get().youtubeDownloadTimeoutSeconds);
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("YouTube download timed out after " + timeoutSeconds + "s");
        }
        return new DownloadAttempt(process.exitValue(), outputLines);
    }

    private Path locatePreparedOutput(Path expectedOutput, List<String> outputLines) throws IOException {
        if (Files.isRegularFile(expectedOutput)) {
            return expectedOutput;
        }
        Path printedOutput = tryFindPrintedOutput(outputLines);
        if (printedOutput != null && Files.isRegularFile(printedOutput)) {
            Files.createDirectories(expectedOutput.getParent());
            Files.move(printedOutput, expectedOutput, StandardCopyOption.REPLACE_EXISTING);
            return expectedOutput;
        }
        return null;
    }

    private boolean isManagedCacheFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(PRIMARY_OUTPUT_EXTENSION) || name.endsWith(FALLBACK_OUTPUT_EXTENSION);
    }

    private record DownloadAttempt(int exitCode, List<String> outputLines) {
    }

    public record PreparedTrack(String videoId, Path localPath, boolean fromCache, boolean converted) {
    }
}
