package com.codexceed.xmusic.audio.decoders;

import com.codexceed.xmusic.XMusic;
import com.codexceed.xmusic.audio.AudioDecoder;
import net.sourceforge.jaad.aac.AACException;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.SampleBuffer;
import net.sourceforge.jaad.mp4.MP4Container;
import net.sourceforge.jaad.mp4.api.AudioTrack;
import net.sourceforge.jaad.mp4.api.Frame;
import net.sourceforge.jaad.mp4.api.Movie;
import net.sourceforge.jaad.mp4.api.Track;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.List;

/**
 * AAC / MP4 decoder backed by JAAD (Java Advanced Audio Decoder).
 * This handles both pure audio MP4/M4A and progressive MP4 containers that
 * still carry an AAC track.
 */
public final class AacDecoder implements AudioDecoder {

    @Override
    public AudioInputStream decode(InputStream source) throws Exception {
        File tempFile = bufferToTempFile(source);

        try {
            MP4Container container = new MP4Container(new RandomAccessFile(tempFile, "r"));
            Movie movie = container.getMovie();
            List<Track> tracks = movie.getTracks(AudioTrack.AudioCodec.AAC);

            if (tracks.isEmpty()) {
                throw new AACException("No AAC audio track found in M4A container");
            }

            AudioTrack audioTrack = (AudioTrack) tracks.get(0);
            Decoder decoder = new Decoder(audioTrack.getDecoderSpecificInfo());
            StreamingAacInputStream pcmStream = new StreamingAacInputStream(audioTrack, decoder, tempFile);

            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    pcmStream.getSampleRate(),
                    16,
                    pcmStream.getChannels(),
                    pcmStream.getChannels() * 2,
                    pcmStream.getSampleRate(),
                    false
            );

            return new AudioInputStream(pcmStream, decodedFormat, AudioSystem.NOT_SPECIFIED);
        } catch (Exception e) {
            tempFile.delete();
            throw e;
        }
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{".m4a", ".aac", ".mp4a"};
    }

    @Override
    public boolean canDecode(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        for (String ext : getSupportedExtensions()) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        return lower.contains("mime=audio%2fmp4")
                || lower.contains("mime=audio/mp4")
                || lower.contains("mime=video%2fmp4")
                || lower.contains("mime=video/mp4")
                || lower.contains("itag=139")
                || lower.contains("itag=140")
                || lower.contains("itag=141")
                || lower.contains("itag=18")
                || lower.contains("itag=22")
                || lower.contains("itag=37")
                || lower.contains("itag=38");
    }

    private static File bufferToTempFile(InputStream source) throws IOException {
        File temp = File.createTempFile("xmusic-aac-", ".m4a");
        temp.deleteOnExit();

        try (FileOutputStream fos = new FileOutputStream(temp)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = source.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }

        return temp;
    }

    private static final class StreamingAacInputStream extends InputStream {
        private final AudioTrack audioTrack;
        private final Decoder decoder;
        private final SampleBuffer sampleBuffer;
        private final File tempFile;

        private byte[] pcmBuffer = new byte[0];
        private int pcmOffset = 0;
        private boolean endOfStream = false;
        private int sampleRate;
        private int channels;

        private StreamingAacInputStream(AudioTrack audioTrack, Decoder decoder, File tempFile) {
            this.audioTrack = audioTrack;
            this.decoder = decoder;
            this.sampleBuffer = new SampleBuffer();
            this.tempFile = tempFile;
            this.sampleRate = (int) audioTrack.getSampleRate();
            this.channels = audioTrack.getChannelCount();

            try {
                decodeNextFrame();
            } catch (Exception e) {
                XMusic.LOGGER.warn("Failed to decode initial AAC frame", e);
            }
        }

        private int getSampleRate() {
            return sampleRate;
        }

        private int getChannels() {
            return channels;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int read = read(single, 0, 1);
            return read == -1 ? -1 : single[0] & 0xFF;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            if (target == null) {
                throw new NullPointerException("target");
            }
            if (offset < 0 || length < 0 || length > target.length - offset) {
                throw new IndexOutOfBoundsException();
            }
            if (length == 0) {
                return 0;
            }

            int totalRead = 0;
            while (length > 0) {
                if (pcmOffset >= pcmBuffer.length) {
                    try {
                        if (!decodeNextFrame()) {
                            break;
                        }
                    } catch (Exception e) {
                        throw new IOException("Failed to decode AAC frame", e);
                    }
                }

                int available = pcmBuffer.length - pcmOffset;
                int toCopy = Math.min(length, available);
                System.arraycopy(pcmBuffer, pcmOffset, target, offset, toCopy);
                pcmOffset += toCopy;
                offset += toCopy;
                length -= toCopy;
                totalRead += toCopy;
            }

            return totalRead > 0 ? totalRead : -1;
        }

        @Override
        public void close() {
            try {
                tempFile.delete();
            } catch (Exception ignored) {
            }
        }

        private boolean decodeNextFrame() throws Exception {
            if (endOfStream) {
                return false;
            }

            if (!audioTrack.hasMoreFrames()) {
                endOfStream = true;
                pcmBuffer = new byte[0];
                pcmOffset = 0;
                return false;
            }

            Frame frame = audioTrack.readNextFrame();
            decoder.decodeFrame(frame.getData(), sampleBuffer);

            sampleRate = sampleBuffer.getSampleRate();
            channels = sampleBuffer.getChannels();
            byte[] data = sampleBuffer.getData();
            pcmBuffer = new byte[data.length];
            System.arraycopy(data, 0, pcmBuffer, 0, data.length);
            pcmOffset = 0;
            return true;
        }
    }
}
