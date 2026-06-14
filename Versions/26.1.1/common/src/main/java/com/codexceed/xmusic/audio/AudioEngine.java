    package com.codexceed.xmusic.audio;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.audio.decoders.AacDecoder;
import com.codexceed.xmusic.audio.decoders.JavaSoundDecoder;
import com.codexceed.xmusic.audio.decoders.Mp3Decoder;
import com.codexceed.xmusic.audio.decoders.OggDecoder;
import net.minecraft.client.Minecraft;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Core audio engine that manages decoding and playback.
 * Operates as a singleton; the OpenAL output must be updated on the
 * main/render thread each tick.
 *
 * <h3>Thread Safety</h3>
 * <ul>
 *   <li>Singleton uses holder idiom for thread-safe lazy init.</li>
 *   <li>Listeners use CopyOnWriteArrayList for safe iteration during callbacks.</li>
 *   <li>State is managed via AtomicReference to prevent torn reads.</li>
 *   <li>playbackGeneration counter prevents stale decoder threads from
 *       interfering with a newer play request.</li>
 * </ul>
 */
public class AudioEngine {
    /** Playback state machine */
    public enum State {
        IDLE, LOADING, PLAYING, PAUSED, STOPPED
    }

    // Thread-safe lazy singleton via holder idiom
    private static final class Holder {
        static final AudioEngine INSTANCE = new AudioEngine();
    }

    private final OpenALOutput output = new OpenALOutput();
    private final List<AudioDecoder> decoders = new ArrayList<>();
    private final CopyOnWriteArrayList<AudioEventListener> listeners = new CopyOnWriteArrayList<>();
    private volatile ExecutorService decoderThread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "XMusic-Decoder");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private volatile AudioTrack currentTrack;
    private volatile long playbackStartTime = 0;
    private volatile long pausedPosition = 0;
    private volatile boolean outputInitialized = false;
    private final AtomicInteger playbackGeneration = new AtomicInteger();
    /** Resolved duration from audio format/file size when track.durationMs is 0. */
    private volatile long resolvedDurationMs = 0;

    private AudioEngine() {
        decoders.add(new AacDecoder());
        decoders.add(new Mp3Decoder());
        decoders.add(new OggDecoder());
        decoders.add(new JavaSoundDecoder());
    }

    public static AudioEngine getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Initialize the OpenAL output. Must be called from the render thread.
     */
    public boolean init() {
        return true;
    }

    private volatile PcmStreamBuffer activeBuffer;

    /**
     * Play a track. Decoding happens on a background thread that pumps
     * PCM into a PcmStreamBuffer ring buffer. The render thread reads
     * from the buffer via OpenALOutput each tick.
     */
    public void play(AudioTrack track) {
        play(track, 0L, true);
    }

    /**
     * Play a pre-decoded PCM stream (used by YouTube streaming path).
     * The PCM data is piped directly to OpenAL without going through
     * the decoder chain.
     */
    public void playPcmStream(AudioTrack track, AudioInputStream pcm) {
        if (track == null || pcm == null) {
            return;
        }

        int generation = playbackGeneration.incrementAndGet();
        state.set(State.LOADING);
        currentTrack = track;
        pausedPosition = 0L;
        playbackStartTime = System.currentTimeMillis();
        notifyBuffering(track);

        // Wrap the pre-decoded stream in a PcmStreamBuffer via a pump thread
        PcmStreamBuffer buffer = new PcmStreamBuffer(pcm.getFormat());
        activeBuffer = buffer;
        Thread pumpThread = new Thread(() -> {
            byte[] tmp = new byte[16384];
            try {
                int n;
                while ((n = pcm.read(tmp)) != -1 && !buffer.isClosed()) {
                    if (n > 0) buffer.write(tmp, 0, n);
                }
            } catch (Exception e) {
                XMusic.LOGGER.debug("PCM pump ended: {}", e.getMessage());
            } finally {
                buffer.markEof();
                try { pcm.close(); } catch (Exception ignored) {}
            }
        }, "XMusic-PCM-Pump");
        pumpThread.setDaemon(true);
        buffer.setWriterThread(pumpThread);
        pumpThread.start();

        Minecraft.getInstance().execute(() -> startBufferPlayback(track, buffer, 0L, true, generation));
    }

    private void play(AudioTrack track, long startPositionMs, boolean autoStart) {
        if (track == null) return;

        XMusic.LOGGER.info("[AudioEngine] play('{}') uri='{}' startMs={} autoStart={}",
                track.getDisplayName(), track.getUri(), startPositionMs, autoStart);

        int generation = playbackGeneration.incrementAndGet();
        state.set(State.LOADING);
        currentTrack = track;
        pausedPosition = Math.max(0L, startPositionMs);
        playbackStartTime = System.currentTimeMillis() - pausedPosition;
        notifyBuffering(track);

        // Kill the old decoder thread immediately so it doesn't block the new seek
        // Shutdown the old executor, close the old buffer, then recreate the executor
        ExecutorService oldDecoder = decoderThread;
        decoderThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "XMusic-Decoder");
            t.setDaemon(true);
            return t;
        });
        oldDecoder.shutdownNow(); // interrupts the old decoder thread

        // Close any previous buffer
        PcmStreamBuffer oldBuffer = activeBuffer;
        activeBuffer = null;
        if (oldBuffer != null) oldBuffer.close();

        // Stop output synchronously (we're likely on render thread already for seek,
        // or we schedule it for new plays)
        if (outputInitialized) {
            try { output.stop(); } catch (Exception ignored) {}
        }

        decoderThread.submit(() -> {
            try {
                InputStream raw = openStream(track);
                if (raw == null) {
                    XMusic.LOGGER.error("[AudioEngine] openStream returned null for uri='{}'", track.getUri());
                    notifyError("Cannot open audio source: " + track.getUri(), null);
                    state.set(State.IDLE);
                    return;
                }

                AudioDecoder decoder = findDecoder(track.getUri());
                if (decoder == null) {
                    XMusic.LOGGER.error("[AudioEngine] No decoder found for uri='{}'", track.getUri());
                    notifyError("No decoder for: " + track.getUri(), null);
                    raw.close();
                    state.set(State.IDLE);
                    return;
                }

                XMusic.LOGGER.info("[AudioEngine] Decoding '{}' with {}", track.getDisplayName(), decoder.getClass().getSimpleName());
                AudioInputStream pcm = decoder.decode(new BufferedInputStream(raw));
                skipToPosition(pcm, startPositionMs);

                // Create ring buffer and pump decoded PCM into it on this thread
                PcmStreamBuffer buffer = new PcmStreamBuffer(pcm.getFormat());
                buffer.setWriterThread(Thread.currentThread());
                activeBuffer = buffer;

                // Resolve duration from decoded stream if track didn't provide one
                boolean needsDurationEstimate = track.getDurationMs() <= 0;
                if (needsDurationEstimate) {
                    try {
                        javax.sound.sampled.AudioFormat fmt = pcm.getFormat();
                        long frameLength = pcm.getFrameLength(); // total frames if known
                        float sampleRate = fmt.getSampleRate();
                        int frameSize = fmt.getFrameSize();
                        if (frameLength > 0 && sampleRate > 0) {
                            // Precise duration from decoded stream metadata
                            resolvedDurationMs = (long) (frameLength * 1000L / sampleRate);
                            XMusic.LOGGER.info("[AudioEngine] Resolved duration from frameLength: {}ms", resolvedDurationMs);
                        } else if (sampleRate > 0 && frameSize > 0) {
                            // Estimate from file size + actual bitrate from decoder
                            try {
                                String uri = track.getUri();
                                java.io.File f;
                                if (uri.startsWith("file://") || uri.startsWith("file:/")) {
                                    f = new java.io.File(new URI(uri));
                                } else {
                                    f = new java.io.File(uri);
                                }
                                if (f.exists()) {
                                    long fileSize = f.length();
                                    long estimatedDurationMs = -1;

                                    // Use actual bitrate from MP3 decoder if available
                                    if (decoder instanceof Mp3Decoder) {
                                        int bitrate = ((Mp3Decoder) decoder).getBitrateFromHeader();
                                        if (bitrate > 0) {
                                            // JLayer Header.bitrate() returns bps (e.g. 128000 for 128kbps)
                                            // duration_ms = (file_size_bytes * 8 * 1000) / bitrate_bps
                                            estimatedDurationMs = (fileSize * 8L * 1000L) / bitrate;
                                            XMusic.LOGGER.info("[AudioEngine] Resolved duration from MP3 bitrate {}bps: {}ms", bitrate, estimatedDurationMs);
                                        }
                                    }

                                    // Fallback: estimate from file size + PCM format
                                    if (estimatedDurationMs <= 0) {
                                        // Rough estimate — will be corrected by exact PCM count after decode finishes
                                        // Assume ~128kbps for MP3 (ratio ~11), ~192kbps for OGG (ratio ~8)
                                        String lowerUri = uri.toLowerCase();
                                        long ratio;
                                        if (lowerUri.endsWith(".mp3")) ratio = 11L;
                                        else if (lowerUri.endsWith(".ogg")) ratio = 8L;
                                        else if (lowerUri.endsWith(".flac")) ratio = 2L;
                                        else if (lowerUri.endsWith(".wav")) ratio = 1L;
                                        else ratio = 10L;
                                        long estimatedPcmBytes = fileSize * ratio;
                                        estimatedDurationMs = estimatedPcmBytes * 1000L / (frameSize * (long) sampleRate);
                                        XMusic.LOGGER.info("[AudioEngine] Estimated duration from file size (ratio {}x): {}ms [will refine after decode]", ratio, estimatedDurationMs);
                                    }

                                    resolvedDurationMs = estimatedDurationMs;
                                }
                            } catch (Exception ignored2) {}
                        }
                    } catch (Exception ignored) {}
                } else {
                    resolvedDurationMs = track.getDurationMs();
                }

                // Schedule render-thread handoff: OpenALOutput reads from buffer
                Minecraft.getInstance().execute(() ->
                        startBufferPlayback(track, buffer, startPositionMs, autoStart, generation));

                // Pump: read from decoder, write to ring buffer (blocks when buffer full)
                byte[] tmp = new byte[16384];
                int n;
                while ((n = pcm.read(tmp)) != -1 && !buffer.isClosed()) {
                    if (n > 0) buffer.write(tmp, 0, n);
                }
                buffer.markEof();
                pcm.close();

                // Compute exact duration from actual decoded PCM bytes — only for tracks
                // that didn't provide their own duration (don't override valid YouTube durations).
                // PCM bytes are the most accurate source since they measure actual decoded audio,
                // unlike bitrate+filesize which overestimates due to ID3 tags and album art.
                if (needsDurationEstimate) {
                    long totalPcmBytes = buffer.getTotalBytesWritten();
                    if (totalPcmBytes > 0 && buffer.getFormat().getSampleRate() > 0 && buffer.getFormat().getFrameSize() > 0) {
                        long pcmPortionMs = (totalPcmBytes * 1000L) / ((long) buffer.getFormat().getFrameSize() * (long) buffer.getFormat().getSampleRate());
                        long exactDurationMs = startPositionMs + pcmPortionMs;
                        if (exactDurationMs > 0) {
                            resolvedDurationMs = exactDurationMs;
                            XMusic.LOGGER.info("[AudioEngine] Exact duration from decoded PCM: {}ms (startOffset={}ms + pcmPortion={}ms, {} bytes)", exactDurationMs, startPositionMs, pcmPortionMs, totalPcmBytes);
                        }
                    }
                }

                XMusic.LOGGER.info("[AudioEngine] Decoder finished for '{}'", track.getDisplayName());

            } catch (Exception e) {
                // InterruptedException is expected when seeking — don't log as error
                if (e instanceof InterruptedException || e.getCause() instanceof InterruptedException) {
                    XMusic.LOGGER.debug("[AudioEngine] Decoder interrupted (seek/stop)");
                    return;
                }
                XMusic.LOGGER.error("[AudioEngine] Failed to play track: {}", track.getDisplayName(), e);
                if (generation == playbackGeneration.get()) {
                    notifyError("Playback failed: " + e.getMessage(), e);
                    state.set(State.IDLE);
                }
            }
        });
    }

    /**
     * Pause playback.
     */
    public void pause() {
        if (state.get() == State.PLAYING) {
            output.pause();
            pausedPosition = getPosition();
            state.set(State.PAUSED);
            notifyPaused();
        }
    }

    /**
     * Resume from pause.
     */
    public void resume() {
        if (state.get() == State.PAUSED) {
            output.resume();
            playbackStartTime = System.currentTimeMillis() - pausedPosition;
            state.set(State.PLAYING);
            notifyResumed();
        }
    }

    /**
     * Toggle play/pause.
     */
    public void togglePlayPause() {
        if (state.get() == State.PLAYING) {
            pause();
        } else if (state.get() == State.PAUSED) {
            resume();
        }
    }

    /**
     * Stop playback entirely.
     * Increments generation first to invalidate all in-flight callbacks,
     * then stops the OpenAL output. The output.stop() is safe to call
     * from any thread since it only touches OpenAL state that gets
     * flushed on the next tick anyway.
     */
    public void stop() {
        playbackGeneration.incrementAndGet();
        state.set(State.STOPPED);
        playbackStartTime = 0;
        pausedPosition = 0;
        resolvedDurationMs = 0;

        // Kill decoder thread immediately
        ExecutorService oldDecoder = decoderThread;
        decoderThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "XMusic-Decoder");
            t.setDaemon(true);
            return t;
        });
        oldDecoder.shutdownNow();

        PcmStreamBuffer buf = activeBuffer;
        activeBuffer = null;
        if (buf != null) buf.close();

        // Stop output synchronously
        if (outputInitialized) {
            try { output.stop(); } catch (Exception ignored) {}
        }
        notifyStopped();
    }

    /**
     * Must be called every game tick from the render thread.
     * Keeps the OpenAL buffers filled for continuous playback.
     */
    public void tick() {
        State s = state.get();
        if (s == State.PLAYING) {
            boolean stillPlaying = output.update();
            if (!stillPlaying) {
                // ALWAYS transition state — never leave it stuck at PLAYING
                AudioTrack finished = currentTrack;
                state.set(State.IDLE);
                // Notify listeners so auto-advance can happen
                notifyTrackEnded(finished);
            } else {
                long dur = currentTrack != null ? currentTrack.getDurationMs() : 0;
                if (dur <= 0) dur = resolvedDurationMs;
                notifyProgress(getPosition(), dur);
            }
        } else if (s == State.LOADING) {
            // Report progress during loading so seek bar shows target position
            long dur = currentTrack != null ? currentTrack.getDurationMs() : 0;
            if (dur <= 0) dur = resolvedDurationMs;
            notifyProgress(getPosition(), dur);
        }
    }

    /**
     * Set volume (0.0–1.0).
     */
    public void setVolume(float volume) {
        output.setVolume(volume);
        notifyVolumeChanged(volume);
    }

    /**
     * Seek by reopening and decoding the current track, then skipping decoded PCM.
     * This works for local files and keeps decoder complexity contained.
     */
    public void seek(long positionMs) {
        if (currentTrack == null) return;

        long duration = currentTrack.getDurationMs();
        if (duration <= 0) duration = resolvedDurationMs;
        long clampedPosition = duration > 0L
                ? Math.max(0L, Math.min(positionMs, duration - 250L))
                : Math.max(0L, positionMs);
        // Seek should auto-play even if currently paused
        boolean shouldPlay = state.get() == State.PLAYING || state.get() == State.PAUSED || state.get() == State.LOADING;
        play(currentTrack, clampedPosition, shouldPlay);
    }

    public float getVolume() {
        return output.getVolume();
    }

    /**
     * Get exact playback position in milliseconds from the hardware.
     */
    public long getPosition() {
        State s = state.get();
        if (s == State.PAUSED) return pausedPosition;
        if (s == State.PLAYING) {
            return output.getPositionMs() + pausedPosition;
        }
        if (s == State.LOADING) {
            // During loading, report the seek target position so progress bar shows correctly
            return pausedPosition;
        }
        return 0;
    }

    public State getState() { return state.get(); }
    public AudioTrack getCurrentTrack() { return currentTrack; }
    public boolean isPlaying() { return state.get() == State.PLAYING; }
    public boolean isPaused() { return state.get() == State.PAUSED; }
    public long getResolvedDurationMs() { return resolvedDurationMs; }
    
    /**
     * True if the engine is in a playing state but the hardware buffer has stalled
     * (e.g. waiting for ffmpeg to produce more PCM data).
     */
    public boolean isStalled() {
        return state.get() == State.PLAYING && output.isStalled();
    }

    /**
     * Clean up resources.
     */
    public void shutdown() {
        stop();
        if (outputInitialized) {
            output.destroy();
        }
        decoderThread.shutdownNow();
    }

    // ─────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────

    private InputStream openStream(AudioTrack track) throws Exception {
        String uri = track.getUri();
        if (uri.startsWith("http://") || uri.startsWith("https://")) {
            return URI.create(uri).toURL().openStream();
        } else {
            // Local file
            return new FileInputStream(uri);
        }
    }

    private AudioDecoder findDecoder(String uri) {
        for (AudioDecoder decoder : decoders) {
            if (decoder.canDecode(uri)) return decoder;
        }
        return null;
    }

    private void startBufferPlayback(
            AudioTrack track,
            PcmStreamBuffer buffer,
            long startPositionMs,
            boolean autoStart,
            int generation) {
        try {
            if (generation != playbackGeneration.get()) {
                buffer.close();
                return;
            }

            if (!ensureOutputInitialized()) {
                buffer.close();
                state.set(State.IDLE);
                notifyError("OpenAL output is not ready yet.", null);
                return;
            }

            output.play(buffer);
            playbackStartTime = System.currentTimeMillis() - Math.max(0L, startPositionMs);
            pausedPosition = Math.max(0L, startPositionMs);
            if (autoStart) {
                state.set(State.PLAYING);
            } else {
                output.pause();
                state.set(State.PAUSED);
            }
            notifyTrackStarted(track);

            XMusic.LOGGER.info("Now playing: {}", track.getDisplayName());
        } catch (Exception e) {
            XMusic.LOGGER.error("Failed to start playback on client thread: {}", track.getDisplayName(), e);
            notifyError("Playback failed: " + e.getMessage(), e);
            state.set(State.IDLE);
            buffer.close();
        }
    }

    private boolean ensureOutputInitialized() {
        if (outputInitialized) {
            return true;
        }

        outputInitialized = output.init();
        return outputInitialized;
    }

    private void skipToPosition(AudioInputStream pcm, long positionMs) throws Exception {
        if (pcm == null || positionMs <= 0L) {
            return;
        }

        int frameSize = pcm.getFormat().getFrameSize();
        float frameRate = pcm.getFormat().getFrameRate();
        if (frameSize <= 0 || frameRate <= 0f) {
            return;
        }

        long framesToSkip = (long) ((positionMs / 1000.0) * frameRate);
        long bytesToSkip = framesToSkip * frameSize;
        while (bytesToSkip > 0L) {
            long skipped = pcm.skip(bytesToSkip);
            if (skipped <= 0L) {
                break;
            }
            bytesToSkip -= skipped;
        }
    }

    // ─────────────────────────────────────────────
    //  Listener Management
    // ─────────────────────────────────────────────

    public void addListener(AudioEventListener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(AudioEventListener listener) {
        listeners.remove(listener);
    }

    private void notifyTrackStarted(AudioTrack t) { for (AudioEventListener l : listeners) l.onTrackStarted(t); }
    private void notifyTrackEnded(AudioTrack t) { for (AudioEventListener l : listeners) l.onTrackEnded(t); }
    private void notifyProgress(long pos, long dur) { for (AudioEventListener l : listeners) l.onProgress(pos, dur); }
    private void notifyPaused() { for (AudioEventListener l : listeners) l.onPaused(); }
    private void notifyResumed() { for (AudioEventListener l : listeners) l.onResumed(); }
    private void notifyStopped() { for (AudioEventListener l : listeners) l.onStopped(); }
    private void notifyVolumeChanged(float v) { for (AudioEventListener l : listeners) l.onVolumeChanged(v); }
    private void notifyBuffering(AudioTrack t) { for (AudioEventListener l : listeners) l.onBuffering(t); }
    private void notifyError(String msg, Exception e) { for (AudioEventListener l : listeners) l.onError(msg, e); }

    public void getWaveform(float[] dest) {
        output.getWaveform(dest);
    }

    public float getCurrentAmplitude() {
        return output.getCurrentAmplitude();
    }
}
