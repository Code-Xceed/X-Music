package com.codexceed.xmusic.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.InputStream;

/**
 * Interface for format-specific audio decoders.
 * Implementations convert compressed audio (MP3, OGG) to raw PCM
 * that can be fed into the OpenAL output.
 */
public interface AudioDecoder {

    /**
     * Decode the given input stream into a raw PCM AudioInputStream.
     *
     * @param source the compressed audio data
     * @return an AudioInputStream of decoded PCM data
     * @throws Exception if decoding fails
     */
    AudioInputStream decode(InputStream source) throws Exception;

    /**
     * @return the target PCM format for decoded audio.
     */
    default AudioFormat getTargetFormat() {
        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                44100,   // sample rate
                16,      // sample size in bits
                2,       // channels (stereo)
                4,       // frame size (2 channels * 2 bytes)
                44100,   // frame rate
                false    // little-endian
        );
    }

    /**
     * @return the file extensions this decoder can handle.
     */
    String[] getSupportedExtensions();

    /**
     * Check if this decoder can handle the given file extension.
     */
    default boolean canDecode(String fileName) {
        String lower = fileName.toLowerCase();
        for (String ext : getSupportedExtensions()) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }
}
