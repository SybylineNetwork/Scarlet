package net.sybyline.scarlet.util.tts;

import java.io.Closeable;
import java.io.File;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.sybyline.scarlet.Scarlet;
import net.sybyline.scarlet.ScarletDiscord;
import net.sybyline.scarlet.ScarletEventListener;

public class TtsService implements Closeable
{

    public TtsService(File dir, ScarletEventListener eventListener, ScarletDiscord discord)
    {
        this.provider = TtsProvider.select(dir.toPath());
        this.eventListener = eventListener;
        this.discord = discord;
    }

    final TtsProvider provider;
    final ScarletEventListener eventListener;
    final ScarletDiscord discord;

    public List<String> getInstalledVoices()
    {
        return this.provider.voices();
    }

    public CompletableFuture<Void> submit(String marker, String text)
    {
        text = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return this.provider
            .speak(text, this.eventListener.getTtsVoiceName(), 1.0F, 1.0F)
            .thenAccept(path ->
            {
                String s = "TTS("+marker+"): "+this.discord.submitAudio(path.toFile())+", ";
                try
                {
                    Scarlet.LOG.debug(s + Files.deleteIfExists(path));
                }
                catch (Exception ex)
                {
                    Scarlet.LOG.warn(s + ex, ex);
                }
            });
    }

    @Override
    public void close()
    {
        this.provider.close();
    }

}
