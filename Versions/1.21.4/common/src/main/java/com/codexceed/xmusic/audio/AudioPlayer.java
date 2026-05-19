package com.codexceed.xmusic.audio;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.config.ConfigManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * High-level music player with queue management, shuffle, repeat,
 * and cross-source playback. This is the primary API that screens
 * and services interact with.
 *
 * <p>Wraps {@link AudioEngine} and adds playlist/queue semantics.</p>
 *
 * <h3>Thread Safety</h3>
 * Uses a holder-idiom singleton and CopyOnWriteArrayList for listeners.
 * Queue mutations are synchronized on the queue object itself to prevent
 * concurrent-modification between the render thread and background callbacks.
 */
public class AudioPlayer implements AudioEventListener {
    // Thread-safe lazy singleton via holder idiom
    private static final class Holder {
        static final AudioPlayer INSTANCE = new AudioPlayer();
    }

    private final AudioEngine engine = AudioEngine.getInstance();
    private final List<AudioTrack> queue = new ArrayList<>();
    private final CopyOnWriteArrayList<AudioEventListener> listeners = new CopyOnWriteArrayList<>();
    private volatile int currentIndex = -1;
    private volatile PlaybackMode playbackMode = PlaybackMode.SEQUENTIAL;
    private volatile boolean suppressAutoAdvance = false;
    private List<Integer> shuffleOrder = new ArrayList<>();

    private AudioPlayer() {}

    public static AudioPlayer getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Initialize the audio player and engine.
     */
    public void init() {
        engine.addListener(this);

        // Restore volume from config
        float savedVolume = ConfigManager.get().volume;
        engine.setVolume(savedVolume);

        // Restore playback mode from config
        try {
            playbackMode = PlaybackMode.valueOf(ConfigManager.get().playbackMode);
        } catch (Exception ignored) {
            playbackMode = PlaybackMode.SEQUENTIAL;
        }

        XMusic.LOGGER.info("AudioPlayer initialized. Volume: {}%, Mode: {}",
                (int)(savedVolume * 100), playbackMode.getDisplayName());
    }

    // ─────────────────────────────────────────────
    //  Queue Management
    // ─────────────────────────────────────────────

    /**
     * Set the queue and start playing from the first track.
     */
    public void playQueue(List<AudioTrack> tracks) {
        playQueue(tracks, 0);
    }

    /**
     * Set the queue and start playing from the given index.
     */
    public void playQueue(List<AudioTrack> tracks, int startIndex) {
        synchronized (queue) {
            queue.clear();
            queue.addAll(tracks);
            currentIndex = startIndex;
            generateShuffleOrder();
        }
        playCurrentTrack();
    }

    /**
     * Add a track to the end of the queue.
     */
    public void addToQueue(AudioTrack track) {
        synchronized (queue) {
            queue.add(track);
            generateShuffleOrder();
        }
    }

    /**
     * Play a single track immediately, clearing the queue.
     */
    public void playSingle(AudioTrack track) {
        synchronized (queue) {
            queue.clear();
            queue.add(track);
            currentIndex = 0;
        }
        playCurrentTrack();
    }

    /**
     * Find the index of a track in the queue by URI, or -1 if not found.
     */
    public int findTrackIndex(String uri) {
        synchronized (queue) {
            for (int i = 0; i < queue.size(); i++) {
                if (uri.equals(queue.get(i).getUri())) return i;
            }
        }
        return -1;
    }

    /**
     * Play the track at the given index in the existing queue (no queue clear).
     */
    public void playAtIndex(int index) {
        synchronized (queue) {
            if (index < 0 || index >= queue.size()) return;
            currentIndex = index;
        }
        playCurrentTrack();
    }

    /**
     * Clear the queue and stop playback.
     */
    public void clearQueue() {
        synchronized (queue) {
            queue.clear();
            currentIndex = -1;
        }
        engine.stop();
    }

    // ─────────────────────────────────────────────
    //  Playback Control
    // ─────────────────────────────────────────────

    public void play() {
        if (engine.isPaused()) {
            engine.resume();
        } else if (!engine.isPlaying() && currentIndex >= 0) {
            playCurrentTrack();
        }
    }

    public void pause() {
        engine.pause();
    }

    public void resume() {
        if (engine.isPaused()) {
            engine.resume();
        }
    }

    public void togglePlayPause() {
        engine.togglePlayPause();
    }

    public void stop() {
        engine.stop();
    }

    /**
     * Skip to the next track in the queue.
     */
    public void next() {
        synchronized (queue) {
            if (queue.isEmpty()) return;

            if (playbackMode == PlaybackMode.SHUFFLE) {
                int shufflePos = shuffleOrder.indexOf(currentIndex);
                if (shufflePos < shuffleOrder.size() - 1) {
                    currentIndex = shuffleOrder.get(shufflePos + 1);
                } else {
                    // Wrap shuffled playback with a fresh random order.
                    generateShuffleOrder();
                    currentIndex = shuffleOrder.isEmpty() ? 0 : shuffleOrder.get(0);
                }
            } else {
                currentIndex++;
                if (currentIndex >= queue.size()) {
                    if (playbackMode == PlaybackMode.REPEAT_ALL) {
                        currentIndex = 0;
                    } else {
                        currentIndex = queue.size() - 1;
                        engine.stop();
                        return;
                    }
                }
            }
        }
        playCurrentTrack();
    }

    /**
     * Go to the previous track, or restart current if > 3 seconds in.
     */
    public void previous() {
        synchronized (queue) {
            if (queue.isEmpty()) return;

            // If we're more than 3 seconds into the track, restart it
            if (engine.getPosition() > 3000) {
                // fall through to playCurrentTrack
            } else if (playbackMode == PlaybackMode.SHUFFLE) {
                int shufflePos = shuffleOrder.indexOf(currentIndex);
                if (shufflePos > 0) {
                    currentIndex = shuffleOrder.get(shufflePos - 1);
                }
            } else {
                currentIndex--;
                if (currentIndex < 0) {
                    currentIndex = playbackMode == PlaybackMode.REPEAT_ALL
                            ? queue.size() - 1 : 0;
                }
            }
        }
        playCurrentTrack();
    }

    /**
     * Seek to a position in the current track.
     */
    public void seek(long positionMs) {
        engine.seek(positionMs);
    }

    // ─────────────────────────────────────────────
    //  Volume & Mode
    // ─────────────────────────────────────────────

    public void setVolume(float volume) {
        engine.setVolume(volume);
        ConfigManager.get().volume = volume;
        ConfigManager.save();
    }

    public float getVolume() {
        return engine.getVolume();
    }

    public void setPlaybackMode(PlaybackMode mode) {
        this.playbackMode = mode;
        if (mode == PlaybackMode.SHUFFLE) {
            synchronized (queue) {
                generateShuffleOrder();
            }
        }
        ConfigManager.get().playbackMode = mode.name();
        ConfigManager.save();
        for (AudioEventListener l : listeners) l.onPlaybackModeChanged(mode);
    }

    public void cyclePlaybackMode() {
        setPlaybackMode(playbackMode.next());
    }

    public PlaybackMode getPlaybackMode() {
        return playbackMode;
    }

    // ─────────────────────────────────────────────
    //  State Queries
    // ─────────────────────────────────────────────

    public AudioTrack getCurrentTrack() { return engine.getCurrentTrack(); }
    public boolean isPlaying() { return engine.isPlaying(); }
    public boolean isPaused() { return engine.isPaused(); }
    public long getPosition() { return engine.getPosition(); }
    public AudioEngine.State getState() { return engine.getState(); }

    public List<AudioTrack> getQueue() {
        synchronized (queue) {
            return Collections.unmodifiableList(new ArrayList<>(queue));
        }
    }

    public int getCurrentIndex() { return currentIndex; }

    public int getQueueSize() {
        synchronized (queue) {
            return queue.size();
        }
    }

    /**
     * When true, AudioPlayer will NOT auto-advance to the next track when the
     * current one ends. Used by external backends (e.g. YouTubeNativeBackend)
     * that handle queue advancement through the PlayerFacade instead.
     */
    public void setSuppressAutoAdvance(boolean suppress) {
        this.suppressAutoAdvance = suppress;
    }

    // ─────────────────────────────────────────────
    //  Tick — called every game tick
    // ─────────────────────────────────────────────

    /**
     * Must be called every game tick from the main thread.
     */
    public void tick() {
        engine.tick();
    }

    // ─────────────────────────────────────────────
    //  AudioEventListener — engine callbacks
    // ─────────────────────────────────────────────

    @Override
    public void onTrackEnded(AudioTrack track) {
        // If an external backend (e.g. YouTubeNativeBackend) is handling auto-advance,
        // don't try to advance through our own queue — just forward the event.
        if (suppressAutoAdvance) {
            for (AudioEventListener l : listeners) l.onTrackEnded(track);
            return;
        }

        // Auto-advance based on playback mode
        if (playbackMode == PlaybackMode.REPEAT_ONE) {
            playCurrentTrack(); // Replay same track
        } else {
            next(); // Move to next track
        }
        // Forward event
        for (AudioEventListener l : listeners) l.onTrackEnded(track);
    }

    @Override
    public void onTrackStarted(AudioTrack track) {
        for (AudioEventListener l : listeners) l.onTrackStarted(track);
    }

    @Override
    public void onProgress(long positionMs, long durationMs) {
        for (AudioEventListener l : listeners) l.onProgress(positionMs, durationMs);
    }

    @Override
    public void onPaused() {
        for (AudioEventListener l : listeners) l.onPaused();
    }

    @Override
    public void onResumed() {
        for (AudioEventListener l : listeners) l.onResumed();
    }

    @Override
    public void onStopped() {
        for (AudioEventListener l : listeners) l.onStopped();
    }

    @Override
    public void onError(String message, Exception exception) {
        XMusic.LOGGER.error("Playback error: {}", message, exception);
        for (AudioEventListener l : listeners) l.onError(message, exception);
    }

    // ─────────────────────────────────────────────
    //  Listener management
    // ─────────────────────────────────────────────

    public void addListener(AudioEventListener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(AudioEventListener listener) {
        listeners.remove(listener);
    }

    // ─────────────────────────────────────────────
    //  Internal
    // ─────────────────────────────────────────────

    private void playCurrentTrack() {
        AudioTrack track;
        synchronized (queue) {
            if (currentIndex >= 0 && currentIndex < queue.size()) {
                track = queue.get(currentIndex);
            } else {
                return;
            }
        }
        engine.play(track);
    }

    private void generateShuffleOrder() {
        // Must be called under synchronized (queue)
        shuffleOrder.clear();
        for (int i = 0; i < queue.size(); i++) {
            shuffleOrder.add(i);
        }
        Collections.shuffle(shuffleOrder);
        // Ensure current track is first in shuffle
        if (currentIndex >= 0 && currentIndex < queue.size()) {
            shuffleOrder.remove(Integer.valueOf(currentIndex));
            shuffleOrder.add(0, currentIndex);
        }
    }

    /**
     * Clean up resources on mod unload.
     */
    public void shutdown() {
        engine.shutdown();
    }
}
