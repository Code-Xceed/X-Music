package com.codexceed.xmusic.download;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.service.ServiceManager;
import com.codexceed.xmusic.service.youtube.YouTubeToolManager;
import com.codexceed.xmusic.source.TrackRef;
import com.codexceed.xmusic.util.FolderWatcher;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages track downloads via yt-dlp + ffmpeg.
 * Tracks download state, progress, and persistence.
 */
public final class DownloadManager {
    private static DownloadManager instance;

    private final Map<String, DownloadEntry> entries = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "XMusic-Download");
        t.setDaemon(true);
        return t;
    });

    private Path downloadsDir;
    private Path manifestFile;
    private FolderWatcher folderWatcher;

    private DownloadManager() {}

    public static synchronized DownloadManager getInstance() {
        if (instance == null) instance = new DownloadManager();
        return instance;
    }

    public void init(Path gameDir) {
        Path xmusicDir = gameDir.resolve("xmusic");
        this.downloadsDir = xmusicDir.resolve("downloads");
        this.manifestFile = xmusicDir.resolve("downloads.json");
        try { Files.createDirectories(downloadsDir); } catch (Exception e) {
            XMusic.LOGGER.error("Failed to create downloads dir", e);
        }
        // Migrate old downloads from config/downloads/ to xmusic/downloads/
        migrateOldDownloads(gameDir);
        loadManifest();
        syncFromFolder();
        // Watch for external file changes in downloads folder
        folderWatcher = FolderWatcher.watch(downloadsDir, this::syncFromFolder);
    }

    /** One-time migration: move files from config/downloads/ to xmusic/downloads/ */ 
    private void migrateOldDownloads(Path gameDir) {
        Path oldDir = gameDir.resolve("config").resolve("downloads");
        if (!Files.isDirectory(oldDir)) return;
        try {
            java.io.File[] files = oldDir.toFile().listFiles();
            if (files == null || files.length == 0) return;
            XMusic.LOGGER.info("Migrating {} downloads from config/downloads/ to xmusic/downloads/", files.length);
            for (java.io.File f : files) {
                if (f.isFile() && (f.getName().endsWith(".mp3") || f.getName().endsWith(".ogg"))) {
                    Path dest = downloadsDir.resolve(f.getName());
                    if (!Files.exists(dest)) {
                        Files.move(f.toPath(), dest);
                        XMusic.LOGGER.info("Migrated: {}", f.getName());
                    }
                }
            }
            // Clean up old directory if empty
            files = oldDir.toFile().listFiles();
            if (files == null || files.length == 0) {
                Files.deleteIfExists(oldDir);
                XMusic.LOGGER.info("Removed empty config/downloads/ directory");
            }
        } catch (Exception e) {
            XMusic.LOGGER.warn("Failed to migrate old downloads", e);
        }
    }

    // â”€â”€ Public API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Start downloading a track. If already downloading or done, no-op. */
    public void download(TrackRef track) {
        if (track == null) return;
        String key = trackKey(track);
        DownloadEntry existing = entries.get(key);
        if (existing != null && (existing.state == DownloadState.DOWNLOADING || existing.state == DownloadState.COMPLETED)) {
            return;
        }
        DownloadEntry entry = new DownloadEntry(track);
        entries.put(key, entry);
        executor.submit(() -> doDownload(entry));
    }

    /** Cancel an active download. Removes from entries. */
    public void cancel(TrackRef track) {
        if (track == null) return;
        String key = trackKey(track);
        DownloadEntry entry = entries.remove(key);
        if (entry != null && entry.process != null) {
            entry.process.destroyForcibly();
            entry.state = DownloadState.CANCELLED;
        }
        // Clean up partial file
        if (downloadsDir != null) {
            try { Files.deleteIfExists(outputPath(track)); } catch (Exception ignored) {}
        }
    }

    /** Delete a completed download (removes file + entry). */
    public void deleteDownload(TrackRef track) {
        if (track == null) return;
        String key = trackKey(track);
        entries.remove(key);
        // Delete the downloaded file
        if (downloadsDir != null) {
            try { Files.deleteIfExists(outputPath(track)); } catch (Exception ignored) {}
        }
        saveManifest();
        XMusic.LOGGER.info("Deleted download: {}", track.getTitle());
    }

    /** Get the download state for a track. */
    public DownloadState getState(TrackRef track) {
        if (track == null) return DownloadState.NONE;
        DownloadEntry entry = entries.get(trackKey(track));
        return entry != null ? entry.state : DownloadState.NONE;
    }

    /** Get download progress (0.0 - 1.0). Returns 0 if not downloading. */
    public float getProgress(TrackRef track) {
        if (track == null) return 0f;
        DownloadEntry entry = entries.get(trackKey(track));
        return entry != null ? entry.progress : 0f;
    }

    /** Get all active/recent download entries. */
    public List<DownloadEntry> getEntries() {
        return new ArrayList<>(entries.values());
    }

    /** Check if a track has been downloaded (file exists on disk). */
    public boolean isDownloaded(TrackRef track) {
        if (track == null) return false;
        DownloadEntry entry = entries.get(trackKey(track));
        if (entry != null && entry.state == DownloadState.COMPLETED) return true;
        return Files.exists(outputPath(track));
    }

    /** Get the output file path for a downloaded track. */
    public Path outputPath(TrackRef track) {
        String safeName = sanitizeFileName(track.getTitle() != null ? track.getTitle() : track.getId());
        return downloadsDir.resolve(safeName + ".mp3");
    }

    public Path getDownloadsDir() {
        return downloadsDir;
    }

    /** Rescan the downloads folder and sync entries with what's on disk.
     *  Adds entries for files found on disk that aren't tracked,
     *  removes entries for files that no longer exist.
     */
    public void syncFromFolder() {
        if (downloadsDir == null || !Files.isDirectory(downloadsDir)) return;
        try {
            Set<String> filesOnDisk = new HashSet<>();
            java.io.File[] files = downloadsDir.toFile().listFiles();
            if (files == null) return;

            for (java.io.File f : files) {
                if (!f.isFile()) continue;
                String name = f.getName().toLowerCase();
                if (!name.endsWith(".mp3") && !name.endsWith(".ogg")) continue;
                filesOnDisk.add(f.getName());
            }

            // Remove entries whose files no longer exist on disk
            List<String> toRemove = new ArrayList<>();
            for (Map.Entry<String, DownloadEntry> e : entries.entrySet()) {
                DownloadEntry entry = e.getValue();
                if (entry.state == DownloadState.COMPLETED) {
                    Path expectedPath = outputPath(entry.track);
                    if (!Files.exists(expectedPath)) {
                        toRemove.add(e.getKey());
                    }
                }
            }
            for (String key : toRemove) {
                entries.remove(key);
                XMusic.LOGGER.info("Sync: removed entry for missing file");
            }

            // Add entries for files on disk that aren't tracked
            for (String fileName : filesOnDisk) {
                String nameNoExt = fileName.substring(0, fileName.lastIndexOf('.'));
                // Check if any existing entry already maps to this file
                boolean alreadyTracked = false;
                for (DownloadEntry entry : entries.values()) {
                    if (entry.state == DownloadState.COMPLETED) {
                        Path expectedPath = outputPath(entry.track);
                        if (expectedPath.getFileName().toString().equals(fileName)) {
                            alreadyTracked = true;
                            break;
                        }
                    }
                }
                if (!alreadyTracked) {
                    // Create a TrackRef from the file name
                    String title;
                    String artist;
                    if (nameNoExt.contains(" - ")) {
                        String[] parts = nameNoExt.split(" - ", 2);
                        artist = parts[0].trim();
                        title = parts[1].trim();
                    } else {
                        title = nameNoExt;
                        artist = "Unknown";
                    }
                    Path filePath = downloadsDir.resolve(fileName);
                    String id = "local:" + nameNoExt.hashCode();
                    TrackRef track = new TrackRef.Builder()
                            .id(id)
                            .sourceId("local")
                            .title(title)
                            .artist(artist)
                            .album("")
                            .durationMs(0)
                            .playbackType(com.codexceed.xmusic.source.PlaybackType.NATIVE)
                            .nativeUri(filePath.toString())
                            .build();
                    DownloadEntry entry = new DownloadEntry(track);
                    entry.state = DownloadState.COMPLETED;
                    entry.progress = 1f;
                    entries.put(trackKey(track), entry);
                    XMusic.LOGGER.info("Sync: added entry for {}", fileName);
                }
            }

            if (!toRemove.isEmpty() || filesOnDisk.size() > entries.size()) {
                saveManifest();
            }
        } catch (Exception e) {
            XMusic.LOGGER.warn("Failed to sync downloads folder", e);
        }
    }

    // â”€â”€ Internal â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void doDownload(DownloadEntry entry) {
        TrackRef track = entry.track;
        entry.state = DownloadState.DOWNLOADING;
        entry.progress = 0f;

        YouTubeToolManager tools = ServiceManager.getYouTubeToolManager();
        if (tools == null || !tools.hasYtDlp()) {
            entry.state = DownloadState.FAILED;
            entry.error = "yt-dlp not available";
            XMusic.LOGGER.warn("Download failed: yt-dlp not available for {}", track.getTitle());
            return;
        }

        String ytDlp = tools.getYtDlpExecutable();
        String ffmpeg = tools.hasFfmpeg() ? tools.getFfmpegExecutable() : null;
        Path outFile = outputPath(track);

        // Build yt-dlp command: extract audio, convert to mp3 via ffmpeg
        List<String> cmd = new ArrayList<>();
        cmd.add(ytDlp);
        cmd.add("--no-playlist");
        cmd.add("--extract-audio");
        cmd.add("--audio-format");
        cmd.add("mp3");
        cmd.add("--audio-quality");
        cmd.add("0"); // best quality
        if (ffmpeg != null) {
            // yt-dlp uses --ffmpeg-location (not --ffmpeg-path)
            cmd.add("--ffmpeg-location");
            cmd.add(ffmpeg);
        }
        cmd.add("--newline"); // progress on new lines
        // Output template: force .mp3 extension since --extract-audio --audio-format mp3
        cmd.add("--paths");
        cmd.add(outFile.getParent().toString());
        cmd.add("-o");
        cmd.add(sanitizeFileName(track.getTitle() != null ? track.getTitle() : track.getId()) + ".mp3");
        // Source URL
        String url = resolveUrl(track);
        if (url == null) {
            entry.state = DownloadState.FAILED;
            entry.error = "No URL available for track";
            return;
        }
        cmd.add(url);

        XMusic.LOGGER.info("Starting download: {} -> {}", track.getTitle(), outFile);
        XMusic.LOGGER.debug("Download command: {}", String.join(" ", cmd));

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .redirectErrorStream(true);
            Process process = pb.start();
            entry.process = process;

            StringBuilder lastError = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    XMusic.LOGGER.debug("[yt-dlp] {}", line);
                    // Parse yt-dlp progress: [download]  45.0% of ~3.2MiB at 1.2MiB/s
                    if (line.contains("[download]") && line.contains("%")) {
                        float pct = parseProgress(line);
                        if (pct >= 0) entry.progress = pct;
                    }
                    // Capture error messages
                    if (line.toLowerCase().contains("error") || line.toLowerCase().contains("warning")) {
                        lastError.append(line).append("; ");
                    }
                    // Check if process was cancelled
                    if (entry.state == DownloadState.CANCELLED) {
                        process.destroyForcibly();
                        return;
                    }
                }
            }

            int exitCode = process.waitFor();
            if (entry.state == DownloadState.CANCELLED) return;

            if (exitCode == 0) {
                // yt-dlp may produce a file with a slightly different name
                // (e.g. appending video ID). Find the actual .mp3 file.
                Path actualFile = findDownloadedFile(outFile);
                if (actualFile != null && Files.exists(actualFile)) {
                    // Rename to our canonical name if different
                    if (!actualFile.equals(outFile)) {
                        try {
                            Files.move(actualFile, outFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            XMusic.LOGGER.info("Renamed {} -> {}", actualFile.getFileName(), outFile.getFileName());
                        } catch (Exception e) {
                            // If rename fails, just use the actual file name
                            outFile = actualFile;
                        }
                    }
                    entry.state = DownloadState.COMPLETED;
                    entry.progress = 1f;
                    // Replace track with a NATIVE version pointing to the downloaded MP3
                    entry.track = new TrackRef.Builder()
                            .id(track.getId())
                            .sourceId("local")
                            .title(track.getTitle())
                            .artist(track.getArtist())
                            .album(track.getAlbum())
                            .durationMs(track.getDurationMs())
                            .artworkUrl(track.getArtworkUrl())
                            .playbackType(com.codexceed.xmusic.source.PlaybackType.NATIVE)
                            .nativeUri(outFile.toString())
                            .remoteUri(track.getRemoteUri())
                            .externalUrl(track.getExternalUrl())
                            .build();
                    saveManifest();
                    XMusic.LOGGER.info("Download completed: {} -> {}", track.getTitle(), outFile.getFileName());
                    // Trigger local music rescan so the file appears in Local Files
                    var localService = ServiceManager.getLocalMusic();
                    if (localService != null) localService.scanAsync();
                } else {
                    entry.state = DownloadState.FAILED;
                    entry.error = "Downloaded file not found after yt-dlp completed";
                    XMusic.LOGGER.warn("Download file not found for {}: expected near {}", track.getTitle(), outFile.getParent());
                }
            } else {
                entry.state = DownloadState.FAILED;
                entry.error = lastError.length() > 0 ? lastError.toString() : ("yt-dlp exited with code " + exitCode);
                XMusic.LOGGER.warn("Download failed for {}: exit code {}, error: {}", track.getTitle(), exitCode, entry.error);
                // Clean up partial file on failure
                try { Files.deleteIfExists(outFile); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            if (entry.state != DownloadState.CANCELLED) {
                entry.state = DownloadState.FAILED;
                entry.error = e.getMessage();
                XMusic.LOGGER.error("Download error for " + track.getTitle(), e);
            }
        } finally {
            entry.process = null;
        }
    }

    /**
     * Find the actual downloaded file. yt-dlp with --extract-audio may produce
     * a file with a video ID suffix (e.g. "Title [abc123].mp3") instead of
     * our expected "Title.mp3". Search for any .mp3 matching the base name.
     */
    private Path findDownloadedFile(Path expectedPath) {
        if (Files.exists(expectedPath)) return expectedPath;

        String baseName = sanitizeFileName(
                expectedPath.getFileName().toString().replace(".mp3", ""));
        try {
            for (Path f : Files.newDirectoryStream(expectedPath.getParent(), "*.mp3")) {
                String name = f.getFileName().toString().replace(".mp3", "");
                // Match if the file name starts with our expected base name
                // (yt-dlp appends [videoId] after the title)
                if (name.startsWith(baseName) || name.contains(baseName)) {
                    return f;
                }
            }
        } catch (Exception e) {
            XMusic.LOGGER.warn("Failed to scan downloads dir for {}", baseName, e);
        }
        return null;
    }

    private String resolveUrl(TrackRef track) {
        // Prefer external URL, then remote URI, then native URI
        if (track.getExternalUrl() != null && !track.getExternalUrl().isBlank()) return track.getExternalUrl();
        if (track.getRemoteUri() != null && !track.getRemoteUri().isBlank()) return track.getRemoteUri();
        if (track.getNativeUri() != null && !track.getNativeUri().isBlank()) return track.getNativeUri();
        return null;
    }

    private float parseProgress(String line) {
        try {
            int pctIdx = line.indexOf('%');
            if (pctIdx < 0) return -1f;
            // Find the number before %
            int end = pctIdx;
            int start = end - 1;
            while (start > 0 && (Character.isDigit(line.charAt(start)) || line.charAt(start) == '.' || line.charAt(start) == ' ')) {
                start--;
            }
            String numStr = line.substring(start + 1, end).trim();
            return Float.parseFloat(numStr) / 100f;
        } catch (Exception e) {
            return -1f;
        }
    }

    private String trackKey(TrackRef track) {
        return track.getId() + "|" + track.getSourceId();
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "unknown";
        // Remove characters illegal in filenames
        return name.replaceAll("[\\/:*?\"<>|]", "_").trim();
    }

    // â”€â”€ Persistence â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Save completed downloads manifest to disk. */
    private void saveManifest() {
        if (manifestFile == null) return;
        try (BufferedWriter writer = Files.newBufferedWriter(manifestFile)) {
            writer.write("[");
            boolean first = true;
            for (DownloadEntry entry : entries.values()) {
                if (entry.state != DownloadState.COMPLETED) continue;
                TrackRef t = entry.track;
                if (!first) writer.write(",");
                first = false;
                writer.write("{\"id\":\""); writer.write(escape(t.getId())); writer.write("\"");
                writer.write(",\"sourceId\":\""); writer.write(escape(t.getSourceId())); writer.write("\"");
                writer.write(",\"title\":\""); writer.write(escape(t.getTitle())); writer.write("\"");
                writer.write(",\"artist\":\""); writer.write(escape(t.getArtist())); writer.write("\"");
                writer.write(",\"album\":\""); writer.write(escape(t.getAlbum())); writer.write("\"");
                writer.write(",\"durationMs\":"); writer.write(String.valueOf(t.getDurationMs()));
                writer.write(",\"nativeUri\":\""); writer.write(escape(t.getNativeUri())); writer.write("\"");
                writer.write(",\"remoteUri\":\""); writer.write(escape(t.getRemoteUri())); writer.write("\"");
                writer.write(",\"externalUrl\":\""); writer.write(escape(t.getExternalUrl())); writer.write("\"");
                writer.write("}");
            }
            writer.write("]");
        } catch (Exception e) {
            XMusic.LOGGER.error("Failed to save downloads manifest", e);
        }
    }

    /** Load completed downloads from manifest on startup. */
    private void loadManifest() {
        if (manifestFile == null || !Files.exists(manifestFile)) return;
        try (BufferedReader reader = Files.newBufferedReader(manifestFile)) {
            String content = new String(Files.readAllBytes(manifestFile));
            // Simple JSON array parser
            content = content.trim();
            if (!content.startsWith("[") || !content.endsWith("]")) return;
            content = content.substring(1, content.length() - 1).trim();
            if (content.isEmpty()) return;

            // Split by },{
            String[] objects = content.split("\\},\\s*\\{");
            for (String obj : objects) {
                obj = obj.replaceFirst("^\\{", "").replaceFirst("\\}$", "").trim();
                Map<String, String> fields = parseJsonFields(obj);
                TrackRef track = new TrackRef.Builder()
                        .id(fields.getOrDefault("id", ""))
                        .sourceId("local")
                        .title(fields.getOrDefault("title", "Unknown"))
                        .artist(fields.getOrDefault("artist", "Unknown"))
                        .album(fields.getOrDefault("album", ""))
                        .durationMs(parseLong(fields.getOrDefault("durationMs", "0")))
                        .playbackType(com.codexceed.xmusic.source.PlaybackType.NATIVE)
                        .nativeUri(fields.getOrDefault("nativeUri", ""))
                        .remoteUri(fields.getOrDefault("remoteUri", ""))
                        .externalUrl(fields.getOrDefault("externalUrl", ""))
                        .build();
                // Only add if file still exists on disk
                if (Files.exists(outputPath(track))) {
                    DownloadEntry entry = new DownloadEntry(track);
                    entry.state = DownloadState.COMPLETED;
                    entry.progress = 1f;
                    entries.put(trackKey(track), entry);
                }
            }
            XMusic.LOGGER.info("Loaded {} completed downloads from manifest", entries.size());
        } catch (Exception e) {
            XMusic.LOGGER.error("Failed to load downloads manifest", e);
        }
    }

    private Map<String, String> parseJsonFields(String obj) {
        Map<String, String> map = new LinkedHashMap<>();
        // Simple key-value parser for flat JSON objects
        int i = 0;
        while (i < obj.length()) {
            // Find key
            int keyStart = obj.indexOf('"', i);
            if (keyStart < 0) break;
            int keyEnd = obj.indexOf('"', keyStart + 1);
            if (keyEnd < 0) break;
            String key = unescape(obj.substring(keyStart + 1, keyEnd));
            // Find colon
            int colon = obj.indexOf(':', keyEnd);
            if (colon < 0) break;
            // Find value
            int valStart = colon + 1;
            while (valStart < obj.length() && obj.charAt(valStart) == ' ') valStart++;
            if (valStart >= obj.length()) break;
            String value;
            if (obj.charAt(valStart) == '"') {
                int valEnd = obj.indexOf('"', valStart + 1);
                if (valEnd < 0) break;
                value = unescape(obj.substring(valStart + 1, valEnd));
                i = valEnd + 1;
            } else {
                // Number value
                int valEnd = valStart;
                while (valEnd < obj.length() && obj.charAt(valEnd) != ',' && obj.charAt(valEnd) != '}') valEnd++;
                value = obj.substring(valStart, valEnd).trim();
                i = valEnd;
            }
            map.put(key, value);
            // Skip comma
            while (i < obj.length() && (obj.charAt(i) == ',' || obj.charAt(i) == ' ')) i++;
        }
        return map;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r");
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0; }
    }
}
