package net.sybyline.scarlet.util;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.nio.file.SensitivityWatchEventModifier;

@SuppressWarnings("restriction")
public class TTSService implements Closeable
{

    static final Logger LOG = LoggerFactory.getLogger("Scarlet/TTSService");

    public TTSService(File dir, Listener listener) throws IOException
    {
        this.running = true;
        this.dir = dir;
        this.listener = listener;
        byte[] srcBytes = MiscUtils.readAllBytes(TTSService.class.getResourceAsStream("TTSService.ps1"));
        String sourceString = new String(srcBytes, StandardCharsets.UTF_8);
        String psb64 = Base64.getEncoder().encodeToString(sourceString.getBytes(StandardCharsets.UTF_16LE));
        
        this.cleanDir();
        
        this.proc = Runtime.getRuntime().exec(new String[]
        {
            "powershell",
            "-Version", "5.0",
            "-NoLogo",
//            "-NoExit",
            "-NoProfile",
            "-InputFormat", "Text",
            "-OutputFormat", "Text",
            "-WindowStyle", "Hidden",
            "-ExecutionPolicy", "Bypass",
            "-EncodedCommand", psb64
        }, null, dir);
        this.stdin = new PrintStream(this.proc.getOutputStream());
        this.stdout = this.proc.getInputStream();
        this.stderr = this.proc.getErrorStream();
        this.thread = new Thread(this::thread);
        this.thread.setName("TTSService thread");
        this.thread.setDaemon(true);
        this.thread.start();
        this.stdin.println(dir.getAbsolutePath());
    }

    void cleanDir()
    {
        if (!this.dir.isDirectory())
            this.dir.mkdirs();
        else for (File f : this.dir.listFiles())
            if (f.isFile() && pattern.matcher(f.getName()).matches())
                f.delete();
    }

    public interface Listener
    {
        
        void tts_ready(int job, File file);
        
    }

    public boolean isRunning()
    {
        return this.running;
    }

    public synchronized boolean submit(String line)
    {
        if (!this.running)
            return false;
        if (line == null)
            return false;
        this.stdin.println(line);
        this.stdin.flush();
        return !this.stdin.checkError();
    }

    static final Pattern pattern = Pattern.compile("tts_(?<idx>\\d+)_audio\\.wav");
    volatile boolean running;
    final File dir;
    final Listener listener;
    final Process proc;
    final PrintStream stdin;
    final InputStream stdout, stderr;
    final Thread thread;

    void thread()
    {
        BufferedReader in = new BufferedReader(new InputStreamReader(this.stdout));
        try
        {
            for (String line; this.running && (line = in.readLine()) != null;)
            {
                File file = new File(line);
                String name = file.getName();
                Matcher matcher = pattern.matcher(name);
                if (matcher.find())
                {
                    int idx = Integer.parseInt(matcher.group("idx"));
                    boolean ok = file.isFile() && file.length() > 0L;
                    if (ok) try
                    {
                        this.listener.tts_ready(idx, file);
                    }
                    catch (Exception ex)
                    {
                        
                    }
                }
            }
        }
        catch (IOException ioex)
        {
            LOG.error("Exception in TTSService thread", ioex);
        }
    }

    void thread0()
    {
        Path dirPath = this.dir.toPath();
        Pattern pattern = Pattern.compile("tts_(?<idx>\\d+)_audio\\.wav");
        
        WatchEvent.Kind<?>[] watchKinds =
        {
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
        };
        WatchEvent.Modifier[] watchModifiers =
        {
            SensitivityWatchEventModifier.HIGH,
        };
        
        try (WatchService watch = dirPath.getFileSystem().newWatchService())
        {
            @SuppressWarnings("unused")
            WatchKey wk0 = dirPath.register(watch, watchKinds, watchModifiers);
            for (WatchKey wk; this.running;)
            {
                try
                {
                    wk = watch.take();
                }
                catch (InterruptedException iex)
                {
                    return;
                }
                List<WatchEvent<?>> evts = wk.pollEvents();
                wk.reset();
                for (WatchEvent<?> evt : evts)
                {
                    Path path = (Path)evt.context();
                    String name = path.getFileName().toString();
                    Matcher matcher = pattern.matcher(name);
                    if (matcher.find())
                    {
                        int idx = Integer.parseInt(matcher.group("idx"));
                        Path abs = dirPath.resolve(path);
                        boolean ok;
                        try
                        {
                            ok = Files.exists(abs) && Files.size(abs) > 0L;
                        }
                        catch (IOException ioex)
                        {
                            ok = false;
                        }
                        if (ok) try
                        {
                            this.listener.tts_ready(idx, abs.toFile());
                        }
                        catch (Exception ex)
                        {
                            
                        }
                    }
                }
            }
        }
        catch (IOException ioex)
        {
        }
    }

    @Override
    public synchronized void close() throws IOException
    {
        this.running = false;
        this.thread.interrupt();
        MiscUtils.close(this.stdin);
        MiscUtils.close(this.stdout);
        MiscUtils.close(this.stderr);
        this.cleanDir();
        try
        {
            if (this.proc.waitFor(2, TimeUnit.SECONDS))
                return;
        }
        catch (InterruptedException iex)
        {
        }
        this.proc.destroy();
        try
        {
            if (this.proc.waitFor(2, TimeUnit.SECONDS))
                return;
        }
        catch (InterruptedException iex)
        {
        }
        this.proc.destroyForcibly();
    }

}
