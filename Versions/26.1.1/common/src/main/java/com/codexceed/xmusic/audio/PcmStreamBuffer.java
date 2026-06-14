package com.codexceed.xmusic.audio;

import com.codexceed.xmusic.XMusic;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe ring buffer that bridges a background decoder thread
 * to the render thread's OpenAL output.
 *
 * <p>The decoder thread writes PCM data into a circular byte buffer.
 * The render thread reads from it non-blocking via {@link #available()}
 * and {@link #read(byte[], int, int)}.</p>
 *
 * <p>Reads are always frame-aligned to prevent mid-sample splits
 * that cause audio corruption (e.g. stereo 16-bit = 4 bytes/frame).</p>
 */
public class PcmStreamBuffer extends InputStream {

    private static final int BUFFER_SIZE = 512 * 1024; // 512KB — ~1.4s at 48kHz stereo 16-bit

    private final byte[] ring = new byte[BUFFER_SIZE];
    private int readPos = 0;
    private int writePos = 0;
    private int count = 0; // bytes currently in buffer

    private final AudioFormat format;
    private final int frameSize; // bytes per PCM frame (e.g. 4 for stereo 16-bit)
    private final AtomicBoolean eof = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile Thread writerThread;
    private volatile long totalBytesWritten = 0; // total PCM bytes written by decoder

    public PcmStreamBuffer(AudioFormat format) {
        this.format = format;
        this.frameSize = format.getFrameSize() > 0 ? format.getFrameSize() : 4;
    }

    public AudioFormat getFormat() {
        return format;
    }

    /** Called by the decoder thread to write PCM data. Blocks if buffer is full. */
    public synchronized void write(byte[] data, int offset, int length) throws IOException {
        if (closed.get()) throw new IOException("Buffer closed");
        int written = 0;
        while (written < length) {
            while (count >= BUFFER_SIZE && !closed.get()) {
                try { wait(50); } catch (InterruptedException e) {
                    if (closed.get()) throw new IOException("Buffer closed");
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted", e);
                }
            }
            if (closed.get()) throw new IOException("Buffer closed");

            int space = BUFFER_SIZE - count;
            int chunk = Math.min(length - written, space);

            // Fast ring write using arraycopy (one or two segments)
            int firstSeg = Math.min(chunk, BUFFER_SIZE - writePos);
            System.arraycopy(data, offset + written, ring, writePos, firstSeg);
            if (firstSeg < chunk) {
                // Wraps around — second segment at start of ring
                System.arraycopy(data, offset + written + firstSeg, ring, 0, chunk - firstSeg);
            }
            writePos = (writePos + chunk) % BUFFER_SIZE;
            count += chunk;
            written += chunk;
            totalBytesWritten += chunk;
            notifyAll();
        }
    }

    /**
     * Non-blocking, frame-aligned read for the render thread.
     * Returns 0 if no data available (not EOF), -1 if EOF with no data.
     * Always reads a multiple of frameSize bytes to prevent sample corruption.
     */
    @Override
    public synchronized int read(byte[] target, int offset, int length) throws IOException {
        if (count == 0) {
            return eof.get() ? -1 : 0;
        }

        // Frame-align: round down to nearest frame boundary
        int toRead = Math.min(length, count);
        toRead = (toRead / frameSize) * frameSize;
        if (toRead == 0) return 0;

        // Fast ring read using arraycopy (one or two segments)
        int firstSeg = Math.min(toRead, BUFFER_SIZE - readPos);
        System.arraycopy(ring, readPos, target, offset, firstSeg);
        if (firstSeg < toRead) {
            System.arraycopy(ring, 0, target, offset + firstSeg, toRead - firstSeg);
        }
        readPos = (readPos + toRead) % BUFFER_SIZE;
        count -= toRead;
        notifyAll();
        return toRead;
    }

    @Override
    public int read() throws IOException {
        byte[] single = new byte[1];
        int r = read(single, 0, 1);
        return r <= 0 ? -1 : single[0] & 0xFF;
    }

    /** Non-blocking available() for the render thread. Returns frame-aligned count. */
    @Override
    public synchronized int available() {
        return (count / frameSize) * frameSize;
    }

    /**
     * Blocking wait until at least {@code minBytes} are available in the buffer,
     * or EOF/closed is reached. Used for pre-buffering before starting playback.
     * @return true if enough data is available, false if EOF/closed first.
     */
    public boolean waitForData(int minBytes, long timeoutMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        synchronized (this) {
            while (count < minBytes && !eof.get() && !closed.get()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                try {
                    wait(Math.min(TimeUnit.NANOSECONDS.toMillis(remaining) + 1, 100));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return count >= minBytes;
        }
    }

    /** Called by the decoder thread when it reaches end of stream. */
    public void markEof() {
        eof.set(true);
        synchronized (this) { notifyAll(); }
    }

    /** Close the buffer, unblocking any waiting threads. */
    @Override
    public synchronized void close() {
        closed.set(true);
        notifyAll();
        if (writerThread != null) writerThread.interrupt();
    }

    public boolean isEof() { return eof.get(); }
    public boolean isClosed() { return closed.get(); }

    /** Total PCM bytes written by the decoder so far. Used for accurate duration calculation. */
    public long getTotalBytesWritten() { return totalBytesWritten; }

    void setWriterThread(Thread t) { this.writerThread = t; }
}
