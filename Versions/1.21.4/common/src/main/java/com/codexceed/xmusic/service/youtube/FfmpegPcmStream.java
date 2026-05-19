package com.codexceed.xmusic.service.youtube;

import com.codexceed.xmusic.XMusic;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts FFmpeg as a decoder and exposes its PCM stdout as an AudioInputStream.
 */
public final class FfmpegPcmStream {
    /**
     * 48kHz, 16-bit signed, stereo, little-endian.
     * This is the canonical PCM format that the OpenAL output expects.
     */
    public static final AudioFormat PCM_FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            48000,
            16,
            2,
            4,
            48000,
            false);

    /** Size of the BufferedInputStream wrapping FFmpeg stdout. 128KB gives plenty of read-ahead. */
    private static final int PIPE_BUFFER_SIZE = 131072;

    private FfmpegPcmStream() {
    }

    /**
     * Open an FFmpeg process that decodes {@code inputUrl} and pipes raw PCM
     * to stdout.
     *
     * @param ffmpegExecutable Path to the ffmpeg binary
     * @param inputUrl         URL or local path to the audio source
     * @return An AudioInputStream providing PCM data at 48kHz/16-bit/stereo
     * @throws IOException if ffmpeg cannot be started
     */
    public static AudioInputStream open(String ffmpegExecutable, String inputUrl) throws IOException {
        if (ffmpegExecutable == null || ffmpegExecutable.isBlank()) {
            throw new IOException("ffmpeg is required for instant YouTube streaming.");
        }
        if (inputUrl == null || inputUrl.isBlank()) {
            throw new IOException("A stream URL is required for FFmpeg playback.");
        }

        boolean remoteInput = inputUrl.startsWith("http://") || inputUrl.startsWith("https://");

        List<String> command = new ArrayList<>();
        command.add(ffmpegExecutable);
        command.add("-nostdin");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        command.add("-fflags");
        command.add("nobuffer");
        command.add("-avioflags");
        command.add("direct");
        command.add("-probesize");
        command.add("32768");
        command.add("-analyzeduration");
        // Remote streams need time to receive container headers over the network.
        // Local files have instant header access, so 0 is safe.
        command.add(remoteInput ? "500000" : "0");
        if (remoteInput) {
            command.add("-reconnect");
            command.add("1");
            command.add("-reconnect_streamed");
            command.add("1");
            command.add("-reconnect_delay_max");
            command.add("2");
        }
        command.add("-i");
        command.add(inputUrl);
        command.add("-vn");
        command.add("-f");
        command.add("s16le");
        command.add("-acodec");
        command.add("pcm_s16le");
        command.add("-ar");
        command.add("48000");
        command.add("-ac");
        command.add("2");
        command.add("pipe:1");

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(false)
                .start();

        Thread stderrDrainer = new Thread(() -> drainError(process.getErrorStream()), "XMusic-FFmpegLog");
        stderrDrainer.setDaemon(true);
        stderrDrainer.start();

        InputStream bufferedStdout = new BufferedInputStream(
                new ProcessBackedInputStream(process.getInputStream(), process),
                PIPE_BUFFER_SIZE);

        return new AudioInputStream(bufferedStdout, PCM_FORMAT, AudioSystem.NOT_SPECIFIED);
    }

    private static void drainError(InputStream errorStream) {
        byte[] buffer = new byte[4096];
        try (InputStream in = errorStream) {
            while (in.read(buffer) >= 0) {
                // Drain stderr to prevent FFmpeg from blocking on a full error pipe.
            }
        } catch (IOException e) {
            XMusic.LOGGER.debug("FFmpeg stderr drain ended: {}", e.getMessage());
        }
    }

    /**
     * InputStream wrapper that ensures the FFmpeg process is destroyed
     * when the stream is closed, and properly handles read errors from a
     * dying process.
     */
    private static final class ProcessBackedInputStream extends FilterInputStream {
        private final Process process;

        private ProcessBackedInputStream(InputStream delegate, Process process) {
            super(delegate);
            this.process = process;
        }

        @Override
        public int read() throws IOException {
            try {
                return super.read();
            } catch (IOException e) {
                if (!process.isAlive()) {
                    return -1;
                }
                throw e;
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                return super.read(b, off, len);
            } catch (IOException e) {
                if (!process.isAlive()) {
                    return -1;
                }
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                process.destroy();
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        }
    }
}
