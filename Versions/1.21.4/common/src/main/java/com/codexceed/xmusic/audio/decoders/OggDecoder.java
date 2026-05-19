package com.codexceed.xmusic.audio.decoders;

import com.codexceed.xmusic.audio.AudioDecoder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.InputStream;

/**
 * OGG Vorbis decoder using JOrbis via the javax.sound SPI (vorbisspi).
 * Once vorbisspi is on the classpath, AudioSystem automatically
 * recognizes OGG files.
 */
public class OggDecoder implements AudioDecoder {

    @Override
    public AudioInputStream decode(InputStream source) throws Exception {
        AudioInputStream oggStream = AudioSystem.getAudioInputStream(source);
        AudioFormat baseFormat = oggStream.getFormat();

        // Determine the decoded PCM format
        AudioFormat decodedFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(),
                16,
                baseFormat.getChannels(),
                baseFormat.getChannels() * 2,
                baseFormat.getSampleRate(),
                false
        );

        // Convert OGG to PCM through SPI
        return AudioSystem.getAudioInputStream(decodedFormat, oggStream);
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{".ogg"};
    }
}
