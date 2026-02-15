package net.sybyline.scarlet.util.tts;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class NullTtsProvider implements TtsProvider
{

    public NullTtsProvider(Path dir)
    {
    }

    @Override
    public List<String> voices()
    {
        return Collections.emptyList();
    }

    @Override
    public CompletableFuture<Path> speak(String text, String voiceId, float volume, float speed)
    {
        CompletableFuture<Path> cf = new CompletableFuture<>();
        cf.cancel(true);
        return cf;
    }

    @Override
    public void close()
    {
    }

}
