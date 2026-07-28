package com.codexceed.xmusic.util;

import com.codexceed.xmusic.XMusic;

import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches a directory for file creation, deletion, and modification events.
 * Runs on a daemon thread and fires a callback when changes are detected.
 */
public final class FolderWatcher {
    private final Path directory;
    private final Runnable onChange;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private WatchService watchService;
    private Thread thread;
    private volatile long lastTriggered = 0;
    private static final long DEBOUNCE_MS = 2000;

    private FolderWatcher(Path directory, Runnable onChange) {
        this.directory = directory;
        this.onChange = onChange;
    }

    /**
     * Create and start a folder watcher.
     *
     * @param directory the directory to watch
     * @param onChange  callback invoked (debounced) when files are added/removed/modified
     * @return the watcher instance, or null if watching failed
     */
    public static FolderWatcher watch(Path directory, Runnable onChange) {
        if (directory == null || onChange == null) return null;
        FolderWatcher watcher = new FolderWatcher(directory, onChange);
        if (!watcher.start()) return null;
        return watcher;
    }

    private boolean start() {
        try {
            Files.createDirectories(directory);
            watchService = FileSystems.getDefault().newWatchService();
            directory.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            running.set(true);
            thread = new Thread(this::runLoop, "XMusic-FolderWatcher-" + directory.getFileName());
            thread.setDaemon(true);
            thread.start();
            XMusic.LOGGER.info("FolderWatcher started for: {}", directory);
            return true;
        } catch (Exception e) {
            XMusic.LOGGER.warn("FolderWatcher failed to start for {}: {}", directory, e.getMessage());
            return false;
        }
    }

    private void runLoop() {
        while (running.get()) {
            try {
                WatchKey key = watchService.poll(5, java.util.concurrent.TimeUnit.SECONDS);
                if (key == null) continue;

                boolean hasRelevantChange = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    Path changedFile = directory.resolve((Path) event.context());
                    String name = changedFile.getFileName().toString().toLowerCase();

                    // Only care about music files
                    if (name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".wav")
                            || name.endsWith(".wave") || name.endsWith(".aif") || name.endsWith(".aiff")
                            || name.endsWith(".flac") || name.endsWith(".m4a") || name.endsWith(".opus")) {
                        hasRelevantChange = true;
                    }
                }
                key.reset();

                if (hasRelevantChange) {
                    // Debounce: don't fire more than once per DEBOUNCE_MS
                    long now = System.currentTimeMillis();
                    if (now - lastTriggered >= DEBOUNCE_MS) {
                        lastTriggered = now;
                        try {
                            onChange.run();
                        } catch (Exception e) {
                            XMusic.LOGGER.warn("FolderWatcher callback error for {}: {}", directory, e.getMessage());
                        }
                    }
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                XMusic.LOGGER.warn("FolderWatcher loop error: {}", e.getMessage());
            }
        }
    }

    /** Stop watching. */
    public void stop() {
        running.set(false);
        if (thread != null) thread.interrupt();
        try { if (watchService != null) watchService.close(); } catch (Exception ignored) {}
    }
}
