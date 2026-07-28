package com.codexceed.xmusic.service.local;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.audio.AudioTrack;
import com.codexceed.xmusic.config.ConfigManager;
import com.codexceed.xmusic.util.FolderWatcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Service for scanning and managing local MP3/OGG music files.
 *
 * Default music directory: .minecraft/xmusic/local/
 * User can customize via config.
 *
 * Works completely offline — no API keys needed.
 * Just drop MP3/OGG files in the folder and they appear in the player.
 */
public class LocalMusicService {
    private final CopyOnWriteArrayList<AudioTrack> tracks = new CopyOnWriteArrayList<>();
    private boolean scanning = false;
    private FolderWatcher folderWatcher;

    /**
     * Get the music directory path, creating it if needed.
     */
    public Path getMusicDirectory() {
        String configDir = ConfigManager.get().localMusicDirectory;

        Path dir;
        if (configDir != null && !configDir.isEmpty()) {
            dir = Paths.get(configDir);
        } else {
            // Default: .minecraft/xmusic/local/
            dir = XMusic.getPlatform().getGameDir().resolve("xmusic").resolve("local");
        }

        // Create if it doesn't exist
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            XMusic.LOGGER.error("Could not create music directory: {}", dir, e);
        }

        return dir;
    }

    /**
     * Start an asynchronous scan of the local music directory.
     */
    public void scanAsync() {
        if (scanning) return;

        CompletableFuture.runAsync(() -> {
            scanning = true;
            try {
                scan();
            } catch (Exception e) {
                XMusic.LOGGER.error("Local music scan failed", e);
            } finally {
                scanning = false;
            }
        });

        // Start folder watcher if not already running
        if (folderWatcher == null) {
            folderWatcher = FolderWatcher.watch(getMusicDirectory(), this::scanAsync);
        }
    }

    /**
     * Scan the music directory recursively for MP3 and OGG files.
     * Also scans the downloads folder so completed downloads appear as local tracks.
     */
    public void scan() {
        Path dir = getMusicDirectory();
        XMusic.LOGGER.info("Scanning for local music in: {}", dir);

        List<AudioTrack> found = new ArrayList<>();
        scanDirectory(dir.toFile(), found);

        // Also scan the downloads folder
        Path downloadsDir = XMusic.getPlatform().getGameDir().resolve("xmusic").resolve("downloads");
        if (Files.isDirectory(downloadsDir)) {
            scanDirectory(downloadsDir.toFile(), found);
        }

        tracks.clear();
        tracks.addAll(found);

        XMusic.LOGGER.info("Found {} local music files.", found.size());
    }

    /**
     * Recursively scan a directory for music files.
     */
    private void scanDirectory(File dir, List<AudioTrack> results) {
        if (dir == null || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, results);
            } else if (isMusicFile(file)) {
                AudioTrack track = createTrackFromFile(file);
                if (track != null) {
                    results.add(track);
                }
            }
        }
    }

    /**
     * Create an AudioTrack from a local file.
     * Extracts metadata from filename (and ID3 tags when available).
     */
    private AudioTrack createTrackFromFile(File file) {
        try {
            String fileName = file.getName();
            String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));

            // Try to parse "Artist - Title" format
            String title;
            String artist;

            if (nameWithoutExt.contains(" - ")) {
                String[] parts = nameWithoutExt.split(" - ", 2);
                artist = parts[0].trim();
                title = parts[1].trim();
            } else {
                title = nameWithoutExt;
                artist = "Local";
            }

            // Estimate duration from file size (rough approximation)
            // MP3: ~128kbps = 16KB/s → duration = fileSize / 16000
            // OGG: ~128kbps similar
            long estimatedDurationMs = estimateDurationMs(file);

            // Try basic ID3 tag extraction for MP3 files
            if (fileName.toLowerCase().endsWith(".mp3")) {
                ID3Info id3 = readID3Tags(file);
                if (id3 != null) {
                    if (id3.title != null && !id3.title.isEmpty()) title = id3.title;
                    if (id3.artist != null && !id3.artist.isEmpty()) artist = id3.artist;
                }
            }

            return new AudioTrack.Builder()
                    .id("local:" + file.getAbsolutePath().hashCode())
                    .title(title)
                    .artist(artist)
                    .album(file.getParentFile().getName())
                    .durationMs(estimatedDurationMs)
                    .uri(file.getAbsolutePath())
                    .source(AudioTrack.Source.LOCAL)
                    .build();

        } catch (Exception e) {
            XMusic.LOGGER.warn("Failed to parse local file: {}", file.getName(), e);
            return null;
        }
    }

    /**
     * Very basic ID3v1 tag reader (last 128 bytes of MP3).
     * Avoids any external dependency; just reads raw bytes.
     */
    private ID3Info readID3Tags(File file) {
        try (InputStream is = new FileInputStream(file)) {
            long fileSize = file.length();
            if (fileSize < 128) return null;

            // ID3v1 tags are in the last 128 bytes
            is.skip(fileSize - 128);
            byte[] tag = new byte[128];
            if (is.read(tag) != 128) return null;

            // Check for "TAG" marker
            if (tag[0] != 'T' || tag[1] != 'A' || tag[2] != 'G') return null;

            ID3Info info = new ID3Info();
            info.title = new String(tag, 3, 30, "ISO-8859-1").trim();
            info.artist = new String(tag, 33, 30, "ISO-8859-1").trim();
            info.album = new String(tag, 63, 30, "ISO-8859-1").trim();

            // Filter out garbage
            if (info.title.isEmpty() || info.title.chars().allMatch(c -> c == 0)) {
                info.title = null;
            }
            if (info.artist.isEmpty() || info.artist.chars().allMatch(c -> c == 0)) {
                info.artist = null;
            }

            return info;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if a file is a supported music format.
     */
    private boolean isMusicFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp3")
                || name.endsWith(".ogg")
                || name.endsWith(".wav")
                || name.endsWith(".wave")
                || name.endsWith(".aif")
                || name.endsWith(".aiff")
                || name.endsWith(".au")
                || name.endsWith(".snd")
                || name.endsWith(".flac")
                || name.endsWith(".m4a")
                || name.endsWith(".opus");
    }

    private long estimateDurationMs(File file) {
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
            long frames = stream.getFrameLength();
            float frameRate = stream.getFormat().getFrameRate();
            if (frames > 0 && frameRate > 0) {
                return (long) ((frames / frameRate) * 1000L);
            }
        } catch (Exception ignored) {
            // Some compressed formats do not expose duration here.
        }

        return file.length() / 16;
    }

    /**
     * Get the currently loaded track list.
     */
    public List<AudioTrack> getTracks() {
        return new ArrayList<>(tracks);
    }

    /**
     * Get track count.
     */
    public int getTrackCount() {
        return tracks.size();
    }

    public boolean isScanning() {
        return scanning;
    }

    /** Simple ID3v1 data holder */
    private static class ID3Info {
        String title;
        String artist;
        String album;
    }
}
