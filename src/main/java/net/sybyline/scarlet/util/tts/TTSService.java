package net.sybyline.scarlet.util.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;

public class TTSService {
    private final VoiceProvider provider;
    private final List<AudioSink> sinks;
    private String selectedVoice;
    static final Logger LOG = LoggerFactory.getLogger("Scarlet/TTSService");


    public TTSService(VoiceProvider provider, List<AudioSink> sinks, String defaultVoice) {
        this.provider = provider;
        this.sinks = sinks;
        if (!this.provider.ListAvailableVoices().contains(defaultVoice)) {
            throw new IllegalArgumentException(defaultVoice + " is not available as a voice");
        }
        this.selectedVoice = defaultVoice;
    }

    public boolean submit(String _identifier, String line) {
        String normalized = Normalizer.normalize(line, Normalizer.Form.NFKC) ;
        try {
            Path audioFile = provider.Synthesize(normalized, this.selectedVoice);
            sinks.forEach(s -> s.play(audioFile));
            if (!audioFile.toFile().delete()) {
                LOG.error("Failed to delete audio file: {}", audioFile);
            }

        } catch (VoiceProviderSynthException e) {
            throw new RuntimeException("failed to synthesize speech: ", e);
        }

        return true;
    }

    public boolean selectVoice(String voice) {
        if (!this.getInstalledVoices().contains(voice)) {
            return true;
        }
        this.selectedVoice = voice;

        return false;
    }

    public List<String> getInstalledVoices() {
        return this.provider.ListAvailableVoices();
    }

    public boolean setOutputToDefaultAudioDevice(boolean isToDefaultAudio) {
        return false;
    }

    public synchronized void selectVoiceLater(String name) {
        this.selectVoice(name);
    }

}
