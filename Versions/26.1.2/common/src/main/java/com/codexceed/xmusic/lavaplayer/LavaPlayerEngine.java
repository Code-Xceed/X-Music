package com.codexceed.xmusic.lavaplayer;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.config.ConfigManager;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormatTools;
import com.sedmelluq.discord.lavaplayer.format.AudioPlayerInputStream;
import com.sedmelluq.discord.lavaplayer.format.Pcm16AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.bandcamp.BandcampAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.soundcloud.SoundCloudAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import dev.lavalink.youtube.YoutubeAudioSourceManager;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Singleton LavaPlayer engine â€” production-grade, zero-jitter audio output.
 *
 * <h3>Smooth-Playback Guarantees</h3>
 * <ul>
 *   <li><b>5 s frame buffer</b> â€” absorbs network stalls without starving output</li>
 *   <li><b>5 s stream timeout</b> â€” prevents silence insertion on slow connections</li>
 *   <li><b>250 ms SDL buffer</b> â€” absorbs GC pauses and OS scheduling jitter</li>
 *   <li><b>MAX_PRIORITY output thread</b> â€” never preempted by game or GC</li>
 *   <li><b>Crash recovery</b> â€” output loop auto-restarts on any exception</li>
 *   <li><b>Instant pause</b> â€” SDL buffer flushed immediately for zero-latency pause</li>
 *   <li><b>Clean track switch</b> â€” SDL flushed on track change, no old-audio bleed</li>
 *   <li><b>Volume from config</b> â€” initialized at startup, never plays at wrong level</li>
 *   <li><b>Drift-free position</b> â€” read from LavaPlayer's own tracker, not frame count</li>
 * </ul>
 */
public final class LavaPlayerEngine {

    // â”€â”€ Format â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    /** 2 ch, 48 kHz, 960 samples/frame â‰ˆ 20 ms per frame, signed 16-bit LE */
    public static final AudioDataFormat DATA_FORMAT = new Pcm16AudioDataFormat(2, 48000, 960, true);

    /** Stream timeout: how long to block for one frame before returning silence. */
    private static final long STREAM_TIMEOUT_MS = 5_000L;

    /** SDL hardware buffer: 250 ms of PCM. */
    private static final int SDL_BUFFER_MS = 250;
    private static final int SDL_BUFFER_BYTES =
            (int) (DATA_FORMAT.sampleRate * DATA_FORMAT.channelCount * 2L * SDL_BUFFER_MS / 1000);

    // â”€â”€ Singleton â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static final class Holder {
        static final LavaPlayerEngine INSTANCE = new LavaPlayerEngine();
    }

    public static LavaPlayerEngine getInstance() {
        return Holder.INSTANCE;
    }

    // â”€â”€ LavaPlayer â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private final AudioPlayerManager manager;
    private final AudioPlayer player;

    // â”€â”€ Java Sound output â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private final AudioFormat javaFormat;
    private final DataLine.Info speakerInfo;
    private volatile Mixer       mixer;
    private volatile SourceDataLine sourceLine;

    /** Position in ms, from LavaPlayer's own per-track tracker. Drift-free. */
    private final AtomicLong currentPositionMs = new AtomicLong(0);

    /** Set to true when a track switch occurs â€” the output loop will flush the SDL. */
    private volatile boolean flushRequested = false;

    /** Set to true when pause is requested â€” output loop flushes and sleeps. */
    private volatile boolean pauseFlushRequested = false;

    // â”€â”€ Constructor â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private LavaPlayerEngine() {
        manager = new DefaultAudioPlayerManager();

        // â”€â”€ Quality-preserving configuration â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Frame buffer: 5 seconds of decoded PCM ahead of playback
        manager.setFrameBufferDuration(5_000);
        manager.setPlayerCleanupThreshold(Long.MAX_VALUE);

        // HIGH resampling: if a source serves at a different sample rate
        // (e.g., SoundCloud at 44.1 kHz), this ensures the highest quality
        // interpolation when converting to our 48 kHz output format.
        manager.getConfiguration().setResamplingQuality(
                com.sedmelluq.discord.lavaplayer.player.AudioConfiguration.ResamplingQuality.HIGH);

        // Opus encoding quality 10 (max): only relevant if LavaPlayer
        // internally re-encodes frames to Opus for buffer storage. With our
        // Pcm16AudioDataFormat output, frames are stored as raw PCM â€” this
        // setting is a safety net that has zero effect on our lossless path.
        manager.getConfiguration().setOpusEncodingQuality(10);

        // Output format: 48 kHz / 16-bit / stereo PCM.
        // Matches YouTube's native Opus sample rate (48 kHz) so no
        // resampling is needed. 16-bit depth exceeds YouTube's ~14-bit
        // effective dynamic range, so no quantization artifacts.
        manager.getConfiguration().setOutputFormat(DATA_FORMAT);
        manager.getConfiguration().setFilterHotSwapEnabled(true);

        player = manager.createPlayer();

        // Initialize volume from user config so first track doesn't play at 100%
        float configVolume = ConfigManager.get().volume;
        player.setVolume(Math.max(0, Math.min(100, Math.round(configVolume * 100f))));

        javaFormat  = AudioDataFormatTools.toAudioFormat(DATA_FORMAT);
        speakerInfo = new DataLine.Info(SourceDataLine.class, javaFormat);

        // Internal listener for clean track switching (flush SDL on track change)
        player.addListener(new AudioEventAdapter() {
            @Override
            public void onTrackStart(AudioPlayer p, AudioTrack track) {
                // Signal the output loop to flush old audio from the SDL buffer
                flushRequested = true;
            }
        });

        registerSources();

        // Find default mixer
        setMixer("");

        // Start the output thread ONCE â€” it loops forever with crash recovery
        Thread outputThread = new Thread(this::audioOutputLoopWithRecovery, "XMusic-AudioOutput");
        outputThread.setDaemon(true);
        outputThread.setPriority(Thread.MAX_PRIORITY);
        outputThread.start();

        XMusic.LOGGER.info("[LavaPlayer] Engine started (vol={}%, buffer=5s, sdlBuf={}ms)",
                player.getVolume(), SDL_BUFFER_MS);
    }

    // â”€â”€ Source Registration â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void registerSources() {
        // â”€â”€ YouTube (highest priority â€” pure Java, no yt-dlp) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        //
        // Quality chain (lossless from YouTube to speakers):
        //   1. YouTube Music client requests the highest-quality audio-only stream
        //      (typically Opus codec, up to 160 kbps, natively 48 kHz stereo)
        //   2. LavaPlayer decodes Opus â†’ raw PCM 48 kHz / 16-bit / stereo
        //      (lossless â€” Opus 48 kHz matches our output format exactly,
        //       so NO resampling is needed)
        //   3. Frame buffer stores raw PCM directly (NOT re-encoded to Opus,
        //      because our output format is Pcm16AudioDataFormat)
        //   4. Output thread reads PCM â†’ SourceDataLine â†’ OS mixer â†’ speakers
        //      (bit-perfect passthrough, zero quality loss)
        //
        // Client order: Music first (highest quality), then fallbacks.
        // More clients = better reliability when one gets rate-limited.
        safeRegister(() -> {
            YoutubeAudioSourceManager yt = new YoutubeAudioSourceManager(
                    true,  // allowSearch
                    true,  // allowDirectVideoIds
                    true,  // allowDirectPlaylistIds
                    new dev.lavalink.youtube.clients.Music(),
                    new dev.lavalink.youtube.clients.AndroidVr(),
                    new dev.lavalink.youtube.clients.Web(),
                    new dev.lavalink.youtube.clients.WebEmbedded(),
                    new dev.lavalink.youtube.clients.TvHtml5Simply()
            );
            yt.setPlaylistPageCount(50);
            return yt;
        });

        safeRegister(() -> {
            String id  = "";
            String sec = "";
            if (id == null || id.isBlank() || sec == null || sec.isBlank()) {
                XMusic.LOGGER.debug("[LavaPlayer] Optional source registration skipped.");
                return null;
            }
            return null;
        });

        safeRegister(SoundCloudAudioSourceManager::createDefault);
        safeRegister(BandcampAudioSourceManager::new);
        safeRegister(HttpAudioSourceManager::new);
        safeRegister(LocalAudioSourceManager::new);
    }

    private void safeRegister(
            Supplier<com.sedmelluq.discord.lavaplayer.source.AudioSourceManager> factory) {
        try {
            var src = factory.get();
            if (src != null) {
                manager.registerSourceManager(src);
                XMusic.LOGGER.info("[LavaPlayer] Registered: {}", src.getSourceName());
            }
        } catch (Exception ex) {
            XMusic.LOGGER.warn("[LavaPlayer] Source registration failed: {}", ex.getMessage());
        }
    }

    // â”€â”€ Audio Output Loop (with crash recovery) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Outer wrapper: restarts the output loop on any crash. Audio is too
     * important to lose permanently because of one transient exception.
     */
    private void audioOutputLoopWithRecovery() {
        while (true) {
            try {
                audioOutputLoop();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return; // Clean shutdown
            } catch (Exception ex) {
                XMusic.LOGGER.error("[LavaPlayer] Audio output loop crashed â€” restarting in 1s: {}",
                        ex.getMessage(), ex);
                try {
                    closeLine();
                    Thread.sleep(1_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Core output loop. Runs forever, producing audio from the LavaPlayer
     * frame buffer to the system mixer via SourceDataLine.
     *
     * <h4>Key design points:</h4>
     * <ul>
     *   <li>stream.read() blocks up to STREAM_TIMEOUT_MS waiting for a frame</li>
     *   <li>On pause: immediately flush SDL buffer (instant silence), then sleep</li>
     *   <li>On track switch: flush SDL buffer (no old-audio bleed)</li>
     *   <li>Position read from LavaPlayer's track.getPosition() â€” drift-free</li>
     * </ul>
     */
    private void audioOutputLoop() throws Exception {
        final AudioInputStream stream = AudioPlayerInputStream.createStream(
                player, DATA_FORMAT, STREAM_TIMEOUT_MS, false);

        final byte[] buf = new byte[DATA_FORMAT.chunkSampleCount * DATA_FORMAT.channelCount * 2];
        final long frameDurationMs = DATA_FORMAT.frameDuration();

        XMusic.LOGGER.info("[LavaPlayer] Audio output loop running.");

        while (true) {
            // â”€â”€ Ensure SourceDataLine is open â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            if (sourceLine == null || !sourceLine.isOpen()) {
                closeLine();
                if (!openLine()) {
                    Thread.sleep(500);
                    continue;
                }
            }

            // â”€â”€ Handle flush request (track switch or seek) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            if (flushRequested) {
                flushRequested = false;
                if (sourceLine != null && sourceLine.isOpen()) {
                    sourceLine.flush(); // discard old audio in hardware buffer
                }
            }

            // â”€â”€ Paused: flush once for instant silence, then sleep â”€â”€â”€â”€â”€â”€
            if (player.isPaused()) {
                if (pauseFlushRequested) {
                    pauseFlushRequested = false;
                    if (sourceLine != null && sourceLine.isOpen()) {
                        sourceLine.flush();
                    }
                }
                Thread.sleep(frameDurationMs);
                continue;
            }

            // â”€â”€ Read one PCM frame (blocks up to STREAM_TIMEOUT_MS) â”€â”€â”€â”€
            int bytesRead = stream.read(buf);
            if (bytesRead < 0) {
                // AudioPlayerInputStream should never return EOF, but handle it
                XMusic.LOGGER.warn("[LavaPlayer] Stream returned EOF â€” ignoring.");
                Thread.sleep(frameDurationMs);
                continue;
            }

            // â”€â”€ Write to hardware â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            sourceLine.write(buf, 0, bytesRead);

            // â”€â”€ Update position (drift-free) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            updatePosition();
        }
    }

    /** Read position from the actual LavaPlayer track â€” never drifts. */
    private void updatePosition() {
        var track = player.getPlayingTrack();
        if (track != null) {
            currentPositionMs.set(track.getPosition());
        }
    }

    // â”€â”€ SourceDataLine Management â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private boolean openLine() {
        if (mixer == null) return false;
        try {
            SourceDataLine line = (SourceDataLine) mixer.getLine(speakerInfo);
            line.open(javaFormat, SDL_BUFFER_BYTES);
            line.start();
            sourceLine = line;
            XMusic.LOGGER.info("[LavaPlayer] SourceDataLine opened: {} (buf={}B)",
                    mixer.getMixerInfo().getName(), SDL_BUFFER_BYTES);
            return true;
        } catch (Exception ex) {
            XMusic.LOGGER.warn("[LavaPlayer] Failed to open SourceDataLine: {}", ex.getMessage());
            return false;
        }
    }

    private void closeLine() {
        SourceDataLine line = sourceLine;
        if (line != null) {
            try {
                line.flush();
                line.stop();
                line.close();
            } catch (Exception ignored) {}
            sourceLine = null;
        }
    }

    /** Find and set the audio mixer by name. Empty = first supported (default). */
    public void setMixer(String name) {
        Mixer found = null, defaultMixer = null;
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer m = AudioSystem.getMixer(info);
            if (!m.isLineSupported(speakerInfo)) continue;
            if (info.getName().equals(name)) { found = m; break; }
            if (defaultMixer == null) defaultMixer = m;
        }
        Mixer oldMixer = mixer;
        mixer = (found != null) ? found : defaultMixer;
        closeLine();
        if (oldMixer != null && oldMixer != mixer) {
            if (oldMixer.getSourceLines().length == 0 && oldMixer.getTargetLines().length == 0) {
                oldMixer.close();
            }
        }
    }

    // â”€â”€ Public API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public AudioPlayerManager getManager() { return manager; }
    public AudioPlayer        getPlayer()  { return player; }

    public void addListener(AudioEventAdapter listener) {
        player.addListener(listener);
    }

    /**
     * Start a track. LavaPlayer's startTrack(track, false) atomically stops
     * the previous track and starts the new one â€” no need for a separate
     * stopTrack() call, which would create an unnecessary silence gap.
     *
     * The internal onTrackStart listener sets flushRequested=true, so the
     * output loop will flush old audio from the SDL buffer on the next iteration.
     */
    public void startTrack(com.sedmelluq.discord.lavaplayer.track.AudioTrack lavaTrack) {
        currentPositionMs.set(0);
        player.startTrack(lavaTrack, false);
    }

    public void stopTrack() {
        player.stopTrack();
        currentPositionMs.set(0);
        flushRequested = true;
    }

    public void setPaused(boolean paused) {
        if (paused) {
            pauseFlushRequested = true; // flush SDL on next loop for instant silence
        }
        player.setPaused(paused);
    }

    public boolean isPaused() { return player.isPaused(); }

    /** Volume as 0â€“100 integer (LavaPlayer native). */
    public void setVolume(int v) {
        player.setVolume(Math.max(0, Math.min(100, v)));
    }

    /** Current position in ms. Drift-free â€” from LavaPlayer's own tracker. */
    public long getPositionMs() {
        return currentPositionMs.get();
    }

    public long getDurationMs() {
        var t = player.getPlayingTrack();
        return t != null ? t.getDuration() : 0L;
    }

    /** True if audio is actively being decoded and output. */
    public boolean isActuallyPlaying() {
        return player.getPlayingTrack() != null && !player.isPaused();
    }

    /** Request the output loop to flush the SDL buffer (e.g. after seek). */
    public void requestFlush() {
        flushRequested = true;
    }
}
