package com.codexceed.xmusic.audio.decoders;

import com.codexceed.xmusic.audio.AudioDecoder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.InputStream;

/**
 * Decoder for uncompressed formats supported by JavaSound out of the box.
 */
public class JavaSoundDecoder implements AudioDecoder {

    @Override
    public AudioInputStream decode(InputStream source) throws Exception {
        AudioInputStream input = AudioSystem.getAudioInputStream(source);
        AudioFormat baseFormat = input.getFormat();

        AudioFormat decodedFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                baseFormat.getSampleRate(),
                16,
                baseFormat.getChannels(),
                baseFormat.getChannels() * 2,
                baseFormat.getSampleRate(),
                false
        );

        if (AudioSystem.isConversionSupported(decodedFormat, baseFormat)) {
            return AudioSystem.getAudioInputStream(decodedFormat, input);
        }

        return input;
    }

    @Override
    public String[] getSupportedExtensions() {
        return new String[]{".wav", ".wave", ".aif", ".aiff", ".au", ".snd", ".flac"};
    }
}
