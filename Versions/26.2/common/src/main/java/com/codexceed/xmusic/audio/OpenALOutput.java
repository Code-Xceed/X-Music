package com.codexceed.xmusic.audio;

import com.codexceed.xmusic.XMusic;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bridges decoded PCM audio into Minecraft's OpenAL context.
 * Reads PCM from a {@link PcmStreamBuffer} ring buffer that is fed
 * by a background decoder thread.
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li>Background thread decodes audio and pumps PCM into PcmStreamBuffer.</li>
 *   <li>Render thread calls {@link #update()} each tick, which reads from
 *       the buffer (non-blocking) and fills OpenAL buffers.</li>
 *   <li>PcmStreamBuffer.available() is always accurate, so fillBuffer()
 *       can safely decide whether to read without blocking.</li>
 *   <li>Stall detection handles the case where the decoder is slow to start.</li>
 * </ul>
 */
public class OpenALOutput {
    private static final int BUFFER_COUNT = 8;
    private static final int BUFFER_SIZE = 32768; // 32KB — ~170ms at 48kHz stereo 16-bit

    /** Gain multiplier applied to OpenAL source so music is loud enough relative to Minecraft sounds. */
    private static final float GAIN_MULTIPLIER = 2.5f;

    /** If the source has 0 queued buffers for this many consecutive ticks, declare dead. */
    private static final int STALL_TICK_LIMIT = 100; // ~5 seconds at 20 ticks/sec

    private int source = -1;
    private final int[] buffers = new int[BUFFER_COUNT];
    private final Deque<Integer> freeBufferIds = new ArrayDeque<>(BUFFER_COUNT);
    private PcmStreamBuffer pcmBuffer;
    private AudioFormat format;
    private final AtomicBoolean playing = new AtomicBoolean(false);
    private float volume = 1.0f;
    private boolean streamExhausted = false;
    private int stallTickCounter = 0;
    private long totalBytesProcessed = 0;

    private static final int HISTORY_SIZE = 128 * 1024;
    private final byte[] historyBuffer = new byte[HISTORY_SIZE];
    private long totalBytesWrittenToOpenAL = 0;
    private volatile float currentRms = 0f;

    private final byte[] readBuffer = new byte[BUFFER_SIZE];
    private final ByteBuffer nativeBuffer = BufferUtils.createByteBuffer(BUFFER_SIZE);

    /**
     * Initialize OpenAL source and buffers.
     * Must be called from the render/main thread (OpenAL context).
     */
    public boolean init() {
        try {
            // Safety check: ensure OpenAL capabilities have been set by Minecraft
            try {
                org.lwjgl.openal.AL.getCapabilities();
            } catch (Throwable t) {
                XMusic.LOGGER.warn("OpenAL capabilities not set yet. Deferring initialization.");
                return false;
            }

            source = AL10.alGenSources();
            AL10.alGenBuffers(buffers);

            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                XMusic.LOGGER.error("OpenAL error during init: {}", error);
                return false;
            }

            AL10.alSourcef(source, AL10.AL_GAIN, volume * GAIN_MULTIPLIER);
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
            return true;
        } catch (Exception e) {
            XMusic.LOGGER.error("Failed to initialize OpenAL output", e);
            return false;
        }
    }

    /**
     * Start streaming from a PcmStreamBuffer.
     * Pre-buffers 4 OpenAL buffers (~680ms) before starting playback
     * to prevent initial underruns and glitchy audio.
     */
    public void play(PcmStreamBuffer buffer) {
        stop();

        this.pcmBuffer = buffer;
        this.format = buffer.getFormat();
        this.playing.set(true);
        this.streamExhausted = false;
        this.stallTickCounter = 0;
        this.totalBytesProcessed = 0;
        this.totalBytesWrittenToOpenAL = 0;
        java.util.Arrays.fill(historyBuffer, (byte) 0);

        // All buffers start free
        freeBufferIds.clear();
        for (int buf : buffers) {
            freeBufferIds.add(buf);
        }

        // Pre-buffer: wait for at least 4 buffers worth of PCM data (~680ms)
        // This prevents the initial underrun that causes glitchy audio
        int preBufferTarget = BUFFER_SIZE * 4;
        buffer.waitForData(preBufferTarget, 3000); // wait up to 3s for initial data

        // Fill as many initial buffers as possible (up to 4 for solid pre-buffer)
        int filled = 0;
        while (filled < 4) {
            Integer bufId = freeBufferIds.pollFirst();
            if (bufId == null) break;
            if (!fillBuffer(bufId)) {
                freeBufferIds.addFirst(bufId); // return unused buffer
                break;
            }
            filled++;
        }

        if (filled > 0) {
            AL10.alSourcePlay(source);
            XMusic.LOGGER.info("[OpenAL] Started playback with {} pre-buffers ({}ms buffered)",
                    filled, filled * BUFFER_SIZE * 1000 / (format.getFrameSize() * (int) format.getSampleRate()));
        }
    }

    /**
     * Called every tick to keep buffers filled.
     * Aggressively fills all free buffers to prevent underruns.
     * @return true if still playing, false if finished or dead.
     */
    public boolean update() {
        if (!playing.get() || source == -1) return false;

        // Unqueue all processed buffers, return them to free pool
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        while (processed > 0) {
            int buffer = AL10.alSourceUnqueueBuffers(source);
            int bufferSize = AL10.alGetBufferi(buffer, AL10.AL_SIZE);
            if (bufferSize > 0) {
                totalBytesProcessed += bufferSize;
            }
            freeBufferIds.add(buffer);
            processed--;
        }

        // Aggressively fill ALL free buffers from ring buffer
        while (!freeBufferIds.isEmpty()) {
            Integer bufId = freeBufferIds.peekFirst();
            if (!fillBuffer(bufId)) break;
            freeBufferIds.pollFirst(); // consumed — remove from free pool
        }

        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        int sourceState = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);

        // Check if decoder is done AND ring buffer is empty AND no queued buffers
        if (pcmBuffer != null && pcmBuffer.isEof() && pcmBuffer.available() <= 0 && queued == 0) {
            playing.set(false);
            stallTickCounter = 0;
            return false;
        }

        // Source stopped but we have queued buffers = underrun recovery
        if (sourceState != AL10.AL_PLAYING && sourceState != AL10.AL_PAUSED && playing.get()) {
            if (queued > 0) {
                AL10.alSourcePlay(source);
                stallTickCounter = 0;
            } else if (!streamExhausted) {
                // No buffers queued — try to fill from ring buffer
                boolean refilled = false;
                Integer bufId = freeBufferIds.pollFirst();
                if (bufId != null && fillBuffer(bufId)) {
                    refilled = true;
                    AL10.alSourcePlay(source);
                    stallTickCounter = 0;
                } else if (bufId != null) {
                    freeBufferIds.addFirst(bufId); // return unused
                }
                if (!refilled) {
                    // Check if decoder finished
                    if (pcmBuffer != null && pcmBuffer.isEof() && pcmBuffer.available() <= 0) {
                        streamExhausted = true;
                        playing.set(false);
                        stallTickCounter = 0;
                        return false;
                    }
                    stallTickCounter++;
                    if (stallTickCounter >= STALL_TICK_LIMIT) {
                        XMusic.LOGGER.warn("Audio stream stalled for {}+ ticks — declaring track dead.", STALL_TICK_LIMIT);
                        streamExhausted = true;
                        playing.set(false);
                        stallTickCounter = 0;
                        return false;
                    }
                }
            }
        } else {
            stallTickCounter = 0;
        }

        return true;
    }

    /**
     * Fill an OpenAL buffer with PCM data from the ring buffer.
     * Non-blocking: reads only what's available().
     *
     * @return true if data was written, false if no data available or end of stream.
     */
    private boolean fillBuffer(int buffer) {
        if (pcmBuffer == null) return false;

        try {
            int avail = pcmBuffer.available();
            if (avail <= 0) {
                return false;
            }

            int toRead = Math.min(avail, BUFFER_SIZE);
            int bytesRead = pcmBuffer.read(readBuffer, 0, toRead);
            if (bytesRead <= 0) return false;

            nativeBuffer.clear();
            nativeBuffer.put(readBuffer, 0, bytesRead).flip();

            // Copy to circular history buffer
            for (int i = 0; i < bytesRead; i++) {
                int index = (int) ((totalBytesWrittenToOpenAL + i) % HISTORY_SIZE);
                historyBuffer[index] = readBuffer[i];
            }
            totalBytesWrittenToOpenAL += bytesRead;

            // Calculate RMS of readBuffer
            if (format != null) {
                int frameSize = format.getFrameSize();
                int bytesPerSample = format.getSampleSizeInBits() / 8;
                if (frameSize > 0) {
                    int samples = bytesRead / frameSize;
                    double sum = 0;
                    for (int i = 0; i < samples; i++) {
                        int offset = i * frameSize;
                        short sample = 0;
                        if (bytesPerSample == 2) {
                            sample = (short) ((readBuffer[offset + 1] << 8) | (readBuffer[offset] & 0xFF));
                        } else {
                            sample = (short) ((readBuffer[offset] - 128) << 8);
                        }
                        sum += sample * sample;
                    }
                    double rms = Math.sqrt(sum / (samples > 0 ? samples : 1)) / 32768.0;
                    currentRms = (float) (currentRms * 0.8f + rms * 0.2f);
                }
            }

            int alFormat = getALFormat(format);
            AL10.alBufferData(buffer, alFormat, nativeBuffer, (int) format.getSampleRate());
            AL10.alSourceQueueBuffers(source, buffer);

            return true;
        } catch (Exception e) {
            XMusic.LOGGER.debug("Audio buffer read ended: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Pause playback.
     */
    public void pause() {
        if (source != -1 && playing.get()) {
            AL10.alSourcePause(source);
        }
    }

    /**
     * Resume playback from pause.
     */
    public void resume() {
        if (source != -1 && playing.get()) {
            AL10.alSourcePlay(source);
        }
    }

    /**
     * Stop playback and clean up the buffer.
     */
    public void stop() {
        if (source != -1) {
            AL10.alSourceStop(source);

            int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
            while (queued > 0) {
                AL10.alSourceUnqueueBuffers(source);
                queued--;
            }
        }

        playing.set(false);
        streamExhausted = false;
        stallTickCounter = 0;
        totalBytesProcessed = 0;
        totalBytesWrittenToOpenAL = 0;
        currentRms = 0f;
        java.util.Arrays.fill(historyBuffer, (byte) 0);
        freeBufferIds.clear();

        if (pcmBuffer != null) {
            pcmBuffer.close();
            pcmBuffer = null;
        }
    }

    public void getWaveform(float[] dest) {
        if (!playing.get() || format == null) {
            java.util.Arrays.fill(dest, 0f);
            return;
        }

        long playBytes = getPositionMs() * (format.getFrameSize() * (int) format.getSampleRate()) / 1000L;
        int bytesPerSample = format.getSampleSizeInBits() / 8;
        int channels = format.getChannels();
        int frameSize = bytesPerSample * channels;
        if (frameSize <= 0) {
            java.util.Arrays.fill(dest, 0f);
            return;
        }

        float sampleRate = format.getSampleRate();
        if (sampleRate <= 0) sampleRate = 48000f;
        
        int totalSpanBytes = (int) (0.16f * sampleRate * frameSize);
        int stepBytes = (totalSpanBytes / 32 / frameSize) * frameSize;
        if (stepBytes <= 0) stepBytes = frameSize;

        int avgWindowBytes = (int) (0.024f * sampleRate * frameSize);
        int subSamples = 12;
        int subStep = (avgWindowBytes / subSamples / frameSize) * frameSize;
        if (subStep <= 0) subStep = frameSize;

        for (int i = 0; i < 32; i++) {
            long centerOffset = playBytes + (long) i * stepBytes;
            long startOffset = centerOffset - (avgWindowBytes / 2);

            long sum = 0;
            for (int j = 0; j < subSamples; j++) {
                long offset = startOffset + (long) j * subStep;
                int idx = (int) (offset % HISTORY_SIZE);
                if (idx < 0) idx += HISTORY_SIZE;

                int val = 0;
                if (bytesPerSample == 2) {
                    int b1 = historyBuffer[idx] & 0xFF;
                    int b2 = historyBuffer[(idx + 1) % HISTORY_SIZE];
                    val = (short) ((b2 << 8) | b1);
                } else if (bytesPerSample == 1) {
                    val = historyBuffer[idx] & 0xFF;
                    if (format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED) {
                        val = (byte) val;
                    } else {
                        val -= 128;
                    }
                    val = val << 8;
                }
                sum += Math.abs(val);
            }

            float amplitude = (sum / (float) subSamples) / 32768f;
            if (amplitude > 1f) amplitude = 1f;
            dest[i] = amplitude;
        }
    }

    /**
     * Set the volume (0.0 – 1.0).
     */
    public void setVolume(float vol) {
        this.volume = Math.max(0f, Math.min(1f, vol));
        if (source != -1) {
            AL10.alSourcef(source, AL10.AL_GAIN, this.volume * GAIN_MULTIPLIER);
        }
    }

    public float getVolume() {
        return volume;
    }

    public boolean isPlaying() {
        return playing.get();
    }

    /**
     * Get the exact playback position based on bytes processed by OpenAL.
     */
    public long getPositionMs() {
        if (format == null) return 0;
        int bytesPerSecond = format.getFrameSize() * (int) format.getSampleRate();
        if (bytesPerSecond <= 0) return 0;

        long totalBytes = totalBytesProcessed;

        if (source != -1 && playing.get()) {
            int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            if (state == AL10.AL_PLAYING || state == AL10.AL_PAUSED) {
                totalBytes += AL10.alGetSourcei(source, org.lwjgl.openal.AL11.AL_BYTE_OFFSET);
            }
        }

        return (totalBytes * 1000L) / bytesPerSecond;
    }

    /**
     * True if the stream is currently experiencing an underrun (stalling).
     */
    public boolean isStalled() {
        if (source == -1 || !playing.get()) return false;
        int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        return state != AL10.AL_PLAYING && state != AL10.AL_PAUSED;
    }

    /**
     * Clean up all OpenAL resources.
     */
    public void destroy() {
        stop();
        if (source != -1) {
            AL10.alDeleteSources(source);
            AL10.alDeleteBuffers(buffers);
            source = -1;
        }
    }

    /**
     * Map javax.sound AudioFormat to OpenAL format constant.
     */
    private static int getALFormat(AudioFormat format) {
        if (format.getChannels() == 1) {
            return format.getSampleSizeInBits() == 16
                    ? AL10.AL_FORMAT_MONO16
                    : AL10.AL_FORMAT_MONO8;
        } else {
            return format.getSampleSizeInBits() == 16
                    ? AL10.AL_FORMAT_STEREO16
                    : AL10.AL_FORMAT_STEREO8;
        }
    }

    public float getCurrentAmplitude() {
        return playing.get() ? currentRms : 0f;
    }
}
