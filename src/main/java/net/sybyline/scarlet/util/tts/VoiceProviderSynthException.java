package net.sybyline.scarlet.util.tts;

public class VoiceProviderSynthException extends Exception {
    public VoiceProviderSynthException(String message) {
        super(message);
    }
    public VoiceProviderSynthException(String message, Exception e) {
        super(message, e);
    }
    public VoiceProviderSynthException(Exception e) {
        super(e);
    }
}
