package com.codexceed.xmusic.service.youtube;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.config.XMusicConfig;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Detects and installs yt-dlp / ffmpeg for the YouTube download pipeline.
 */
public final class YouTubeToolManager {
    public enum SetupState {
        CHECKING,
        READY,
        MISSING,
        INSTALLING,
        ERROR
    }

    public enum InstallStep {
        IDLE(""),
        DOWNLOADING_YTDLP("Downloading yt-dlp..."),
        DOWNLOADING_FFMPEG("Downloading ffmpeg..."),
        EXTRACTING("Extracting..."),
        VERIFYING("Verifying tools..."),
        FINISHING("Finishing..."),
        DONE("Done!");

        public final String label;
        InstallStep(String label) { this.label = label; }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // Download URLs per platform
    private static final String YT_DLP_WINDOWS_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe";
    private static final String YT_DLP_LINUX_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp";
    private static final String YT_DLP_MAC_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp";
    private static final String FFMPEG_WINDOWS_URL = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip";
    private static final String FFMPEG_LINUX_URL = "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz";
    private static final String FFMPEG_MAC_URL = "https://evermeet.cx/ffmpeg/getrelease/ffmpeg/zip";

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "XMusic-YouTubeTools");
        thread.setDaemon(true);
        return thread;
    });

    private volatile SetupState state = SetupState.CHECKING;
    private volatile String message = "Checking tools...";
    private volatile String ytDlpExecutable = "";
    private volatile String ffmpegExecutable = "";
    private volatile boolean ytDlpReady;
    private volatile boolean ffmpegReady;
    private volatile InstallStep installStep = InstallStep.IDLE;
    private volatile long lastRefreshAt;
    private volatile CompletableFuture<?> statusRefreshFuture;
    private volatile CompletableFuture<Boolean> activeInstallFuture;

    public void refreshStatusAsync() {
        refreshStatusAsync(false);
    }

    public void refreshStatusAsync(boolean force) {
        if (state == SetupState.INSTALLING) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && (state == SetupState.READY || state == SetupState.MISSING || state == SetupState.ERROR)
                && now - lastRefreshAt < 3000L) {
            return;
        }
        CompletableFuture<?> active = statusRefreshFuture;
        if (active != null && !active.isDone()) {
            return;
        }
        state = SetupState.CHECKING;
        message = "Checking tools...";
        installStep = InstallStep.IDLE;
        statusRefreshFuture = CompletableFuture.runAsync(this::refreshStatus, worker);
    }

    public CompletableFuture<Boolean> installToolsAsync() {
        CompletableFuture<Boolean> existing = activeInstallFuture;
        if (existing != null && !existing.isDone()) {
            return existing;
        }

        state = SetupState.INSTALLING;
        installStep = InstallStep.DOWNLOADING_YTDLP;
        message = installStep.label;
        CompletableFuture<Boolean> installFuture = CompletableFuture.supplyAsync(() -> {
            try {
                refreshStatus();
                boolean needYtDlp = !ytDlpReady;
                boolean needFfmpeg = !ffmpegReady;

                if (!needYtDlp && !needFfmpeg) {
                    state = SetupState.READY;
                    installStep = InstallStep.DONE;
                    message = "All tools ready!";
                    return true;
                }

                if (needYtDlp) {
                    installYtDlp();
                }

                if (needFfmpeg) {
                    installFfmpeg();
                }

                // Verify both tools are actually working before declaring done
                installStep = InstallStep.VERIFYING;
                message = "Verifying tools...";
                refreshStatus();

                // refreshStatus now keeps INSTALLING state if not both ready
                // Only transition to READY/DONE when both are confirmed
                if (ytDlpReady && ffmpegReady) {
                    state = SetupState.READY;
                    installStep = InstallStep.DONE;
                    message = "All tools ready!";
                } else {
                    // Verification failed — one or both tools didn't pass the check
                    installStep = InstallStep.FINISHING;
                    message = "Verification incomplete — some tools may need a restart";
                    state = SetupState.MISSING;
                }
                return state == SetupState.READY;
            } catch (Exception e) {
                state = SetupState.ERROR;
                message = "Install failed: " + firstMessage(e, "Unknown error");
                installStep = InstallStep.IDLE;
                XMusic.LOGGER.error("Failed to install YouTube tools", e);
                return false;
            }
        }, worker).whenComplete((result, error) -> activeInstallFuture = null);
        activeInstallFuture = installFuture;
        return installFuture;
    }

    public SetupState getState() { return state; }
    public String getMessage() { return message; }
    public boolean isReady() { return state == SetupState.READY; }
    public boolean isInstalling() { return state == SetupState.INSTALLING; }
    public String getYtDlpExecutable() { return ytDlpExecutable; }
    public String getFfmpegExecutable() { return ffmpegExecutable; }
    public boolean hasYtDlp() { return ytDlpReady; }
    public boolean hasFfmpeg() { return ffmpegReady; }
    public InstallStep getInstallStep() { return installStep; }

    public Path getBinDirectory() {
        return XMusic.getPlatform().getGameDir().resolve("xmusic").resolve("bin");
    }

    private void refreshStatus() {
        try {
            Files.createDirectories(getBinDirectory());
        } catch (IOException e) {
            state = SetupState.ERROR;
            message = "Failed to create xmusic/bin directory.";
            ytDlpReady = false;
            ffmpegReady = false;
            lastRefreshAt = System.currentTimeMillis();
            return;
        }

        String ytDlp = resolveConfiguredOrLocalExecutable(
                ConfigManager.get().youtubeYtDlpPath,
                isWindows() ? "yt-dlp.exe" : "yt-dlp");
        String ffmpeg = resolveConfiguredOrLocalExecutable(
                ConfigManager.get().youtubeFfmpegPath,
                isWindows() ? "ffmpeg.exe" : "ffmpeg");

        boolean ytReady = isKnownExecutable(ytDlp) || commandAvailable(ytDlp, "--version", 2);
        boolean ffReady = isKnownExecutable(ffmpeg) || commandAvailable(ffmpeg, "-version", 2);
        ytDlpReady = ytReady;
        ffmpegReady = ffReady;

        if (ytReady) {
            ytDlpExecutable = ytDlp;
        } else {
            ytDlpExecutable = "";
        }
        if (ffReady) {
            ffmpegExecutable = ffmpeg;
        } else {
            ffmpegExecutable = "";
        }

        // Only set READY when BOTH tools are verified — don't override INSTALLING state
        if (ytReady && ffReady) {
            maybePersistResolvedPaths(ytDlp, ffmpeg);
            state = SetupState.READY;
            message = "YouTube tools ready.";
            installStep = InstallStep.DONE;
            lastRefreshAt = System.currentTimeMillis();
            return;
        }

        // During installation, keep INSTALLING state — don't downgrade to MISSING
        if (state == SetupState.INSTALLING) {
            // Still installing — keep current state, just update readiness flags
            lastRefreshAt = System.currentTimeMillis();
            return;
        }

        if (ytReady && !ffReady) {
            maybePersistResolvedPaths(ytDlp, ffmpeg);
            state = SetupState.MISSING;
            message = "yt-dlp ready. ffmpeg needed for instant streaming.";
            installStep = InstallStep.IDLE;
        } else {
            state = SetupState.MISSING;
            installStep = InstallStep.IDLE;
            message = "yt-dlp is missing. Install to enable YouTube.";
        }
        lastRefreshAt = System.currentTimeMillis();
    }

    private void maybePersistResolvedPaths(String ytDlp, String ffmpeg) {
        XMusicConfig config = ConfigManager.get();
        boolean changed = false;
        if ((config.youtubeYtDlpPath == null || config.youtubeYtDlpPath.isBlank()) && isAbsoluteExecutable(ytDlp)) {
            config.youtubeYtDlpPath = ytDlp;
            changed = true;
        }
        if ((config.youtubeFfmpegPath == null || config.youtubeFfmpegPath.isBlank()) && isAbsoluteExecutable(ffmpeg)) {
            config.youtubeFfmpegPath = ffmpeg;
            changed = true;
        }
        if (changed) ConfigManager.save();
    }

    private boolean isAbsoluteExecutable(String value) {
        if (value == null || value.isBlank()) return false;
        try { return Path.of(value).isAbsolute(); }
        catch (Exception ignored) { return false; }
    }

    private String resolveConfiguredOrLocalExecutable(String configured, String fileName) {
        if (configured != null && !configured.isBlank()) {
            Path configuredPath = safePath(configured);
            if (configuredPath != null && Files.isRegularFile(configuredPath)) {
                return configuredPath.toString();
            }
        }
        Path local = getBinDirectory().resolve(fileName);
        if (Files.isRegularFile(local)) {
            return local.toString();
        }
        return fileName;
    }

    private boolean commandAvailable(String executable, String versionArg, int timeoutSeconds) {
        try {
            Process process = new ProcessBuilder(executable, versionArg)
                    .redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) { process.destroyForcibly(); return false; }
            return process.exitValue() == 0;
        } catch (Exception ignored) { return false; }
    }

    // ── Install Methods ────────────────────────────────────────────────────

    private void installYtDlp() throws Exception {
        Files.createDirectories(getBinDirectory());
        installStep = InstallStep.DOWNLOADING_YTDLP;
        message = "Downloading yt-dlp...";

        String url = resolveYtDlpDownloadUrl();
        Path output = getBinDirectory().resolve(isWindows() ? "yt-dlp.exe" : "yt-dlp");
        downloadToFile(url, output);

        // Make executable on Unix
        if (!isWindows()) {
            try { output.toFile().setExecutable(true); } catch (Exception ignored) {}
        }

        ConfigManager.get().youtubeYtDlpPath = output.toString();
        ConfigManager.save();
    }

    private void installFfmpeg() throws Exception {
        Files.createDirectories(getBinDirectory());
        installStep = InstallStep.DOWNLOADING_FFMPEG;
        message = "Downloading ffmpeg...";

        String url = resolveFfmpegDownloadUrl();

        if (isWindows()) {
            // Windows: download zip, extract ffmpeg.exe
            Path tempZip = getBinDirectory().resolve("ffmpeg-download.zip");
            try {
                downloadToFile(url, tempZip);
                installStep = InstallStep.EXTRACTING;
                message = "Extracting ffmpeg...";
                extractFfmpegFromZip(tempZip, getBinDirectory().resolve("ffmpeg.exe"));
                ConfigManager.get().youtubeFfmpegPath = getBinDirectory().resolve("ffmpeg.exe").toString();
                ConfigManager.save();
            } finally {
                Files.deleteIfExists(tempZip);
            }
        } else if (isMac()) {
            // macOS: download zip from evermeet.cx
            Path tempZip = getBinDirectory().resolve("ffmpeg-download.zip");
            try {
                downloadToFile(url, tempZip);
                installStep = InstallStep.EXTRACTING;
                message = "Extracting ffmpeg...";
                extractSingleFileFromZip(tempZip, getBinDirectory().resolve("ffmpeg"));
                Path out = getBinDirectory().resolve("ffmpeg");
                try { out.toFile().setExecutable(true); } catch (Exception ignored) {}
                ConfigManager.get().youtubeFfmpegPath = out.toString();
                ConfigManager.save();
            } finally {
                Files.deleteIfExists(tempZip);
            }
        } else {
            // Linux: download static binary tar.xz — try direct binary first
            // Fallback: try apt/brew-style PATH resolution
            Path tempFile = getBinDirectory().resolve("ffmpeg-download.tar.xz");
            try {
                downloadToFile(url, tempFile);
                installStep = InstallStep.EXTRACTING;
                message = "Extracting ffmpeg...";
                // Extract using tar command
                ProcessBuilder pb = new ProcessBuilder("tar", "-xf", tempFile.toString(), "-C", getBinDirectory().toString());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                if (!p.waitFor(30, TimeUnit.SECONDS)) { p.destroyForcibly(); }

                // Find the ffmpeg binary in extracted directory
                Path extracted = findExtractedFfmpeg(getBinDirectory());
                if (extracted != null) {
                    Path target = getBinDirectory().resolve("ffmpeg");
                    Files.move(extracted, target, StandardCopyOption.REPLACE_EXISTING);
                    try { target.toFile().setExecutable(true); } catch (Exception ignored) {}
                    ConfigManager.get().youtubeFfmpegPath = target.toString();
                    ConfigManager.save();
                } else {
                    throw new IOException("Could not find ffmpeg binary in extracted archive");
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private Path findExtractedFfmpeg(Path dir) throws IOException {
        // Search for ffmpeg binary in subdirectories (tar.xz extracts to a folder)
        try (var stream = Files.walk(dir, 3)) {
            for (Path p : stream.toList()) {
                if (p.getFileName().toString().equals("ffmpeg") && Files.isRegularFile(p)) {
                    return p;
                }
            }
        }
        return null;
    }

    private void downloadToFile(String url, Path output) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .header("User-Agent", "CodeX-Music-Player")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode() + " downloading " + url);
        }
        Files.createDirectories(output.getParent());
        try (InputStream in = response.body();
             java.io.OutputStream out = Files.newOutputStream(output)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) out.write(buffer, 0, read);
            }
        }
    }

    private void extractFfmpegFromZip(Path zipFile, Path output) throws Exception {
        try (InputStream fileIn = Files.newInputStream(zipFile);
             ZipInputStream zipIn = new ZipInputStream(fileIn)) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/').toLowerCase();
                if (name.endsWith("/bin/ffmpeg.exe") || name.endsWith("/bin/ffmpeg")) {
                    Files.copy(zipIn, output, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IOException("Could not find ffmpeg in downloaded archive");
    }

    private void extractSingleFileFromZip(Path zipFile, Path output) throws Exception {
        try (InputStream fileIn = Files.newInputStream(zipFile);
             ZipInputStream zipIn = new ZipInputStream(fileIn)) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                // Take the first regular file named "ffmpeg"
                if (!entry.isDirectory() && (name.endsWith("/ffmpeg") || name.equals("ffmpeg"))) {
                    Files.copy(zipIn, output, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IOException("Could not find ffmpeg in downloaded archive");
    }

    private String resolveYtDlpDownloadUrl() {
        String configured = ConfigManager.get().youtubeYtDlpDownloadUrl;
        if (configured != null && !configured.isBlank()) return configured;
        if (isWindows()) return YT_DLP_WINDOWS_URL;
        if (isMac()) return YT_DLP_MAC_URL;
        return YT_DLP_LINUX_URL;
    }

    private String resolveFfmpegDownloadUrl() {
        String configured = ConfigManager.get().youtubeFfmpegDownloadUrl;
        if (configured != null && !configured.isBlank()) return configured;
        if (isWindows()) return FFMPEG_WINDOWS_URL;
        if (isMac()) return FFMPEG_MAC_URL;
        return FFMPEG_LINUX_URL;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private boolean isMac() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac") || os.contains("darwin");
    }

    private boolean isKnownExecutable(String executable) {
        Path path = safePath(executable);
        return path != null && Files.isRegularFile(path);
    }

    private Path safePath(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Path.of(value); }
        catch (Exception ignored) { return null; }
    }

    private String firstMessage(Throwable error, String fallback) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) return current.getMessage();
            current = current.getCause();
        }
        return fallback;
    }
}
