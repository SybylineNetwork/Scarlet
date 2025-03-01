package net.sybyline.scarlet.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;

/**
 * Adapted from org.apache.commons.io.input.Tailer to add preamble and loop functionality
 */
public class Tail implements Runnable
{

    public Tail(File file, Charset charset, long delayMillis, boolean end, boolean reOpen, int bufSize)
    {
        this.file = file;
        this.charset = charset;
        this.delayMillis = delayMillis;
        this.end = end;
        this.reOpen = reOpen;
        this.inbuf = new byte[bufSize];
        this.run = true;
        this.on_init();
    }

    protected final File file;
    protected final Charset charset;
    protected final long delayMillis;
    protected final boolean end;
    protected final boolean reOpen;
    protected final byte[] inbuf;
    protected volatile boolean run;

    protected void on_init() {}
    protected void on_fnf(FileNotFoundException fnfex) {}
    protected void on_rotated() {}
    protected void on_line(boolean inPreamble, String line) {}
    protected void on_exception(Exception ex) {}
    protected void on_eof() {}
    protected void on_loop() {}

    protected boolean isRunning()
    {
        return this.run;
    }

    public void stop()
    {
        this.run = false;
    }

    @Override
    public void run()
    {
        this.tail();
    }

    protected void tail()
    {
        RandomAccessFile reader = null;
        try
        {
            long last = 0,
                 position = 0,
                 fileInitialLength = 0;
            while (this.isRunning() && reader == null)
            {
                try
                {
                    reader = new RandomAccessFile(this.file, "r");// raf mode
                }
                catch (FileNotFoundException e)
                {
                    this.on_fnf(e);
                }
                if (reader == null)
                {
                    Thread.sleep(this.delayMillis);
                }
                else
                {   // current position
                    fileInitialLength = this.file.length();
                    position = this.end ? fileInitialLength : 0;
                    last = MiscUtils.lastModified(this.file);
                    reader.seek(position);
                }
            }
            while (this.isRunning())
            {
                boolean newer = MiscUtils.isNewerThan(this.file, last);
                long length = this.file.length();
                if (length < position)
                {   // rotated
                    this.on_rotated();
                    try (RandomAccessFile save = reader)
                    {
                        reader = new RandomAccessFile(this.file, "r");// raf mode
                        // rotated, read remaining then reopen
                        try
                        {
                            this.readLines(fileInitialLength, save);
                        }
                        catch (IOException ioe)
                        {
                            this.on_exception(ioe);
                        }
                        position = 0;
                        fileInitialLength = 0;
                    }
                    catch (FileNotFoundException e)
                    {
                        this.on_fnf(e);
                        Thread.sleep(this.delayMillis);
                    }
                    continue;
                }
                // not rotated
                if (length > position)
                {   // read appended
                    position = this.readLines(fileInitialLength, reader);
                    last = MiscUtils.lastModified(this.file);
                }
                else if (newer)
                {   // read truncated or overwritten with the exact same length
                    position = 0;
                    fileInitialLength = 0;
                    reader.seek(position);
                    position = this.readLines(fileInitialLength, reader);
                    last = MiscUtils.lastModified(this.file);
                }
                if (this.reOpen && reader != null)
                {
                    reader.close();
                }
                if (this.isRunning())
                {
                    this.on_loop();
                }
                if (this.isRunning())
                {
                    Thread.sleep(this.delayMillis);
                }
                if (this.isRunning() && this.reOpen)
                {
                    reader = new RandomAccessFile(this.file, "r");// raf mode
                    reader.seek(position);
                }
            }
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            this.on_exception(e);
        }
        catch (Exception e)
        {
            this.on_exception(e);
        }
        finally
        {
            this.run = false;
            try
            {
                if (reader != null)
                {
                    reader.close();
                }
            }
            catch (IOException e)
            {
                this.on_exception(e);
            }
        }
    }

    /**
     * Read new lines.
     *
     * @param fileInitialLength The length of the file when it was initially opened or zero
     * @param reader The file to read
     * @return The new position after the lines have been read
     * @throws java.io.IOException if an I/O error occurs.
     */
    protected long readLines(long fileInitialLength, RandomAccessFile reader) throws IOException
    {
        try (ByteArrayOutputStream lineBuf = new ByteArrayOutputStream(64))
        {
            long pos = reader.getFilePointer();
            long rePos = pos;
            int num;
            boolean seenCR = false;
            while (this.isRunning() && ((num = reader.read(this.inbuf)) != -1)) // eof
            {
                for (int i = 0; i < num; i++)
                {
                    byte ch = this.inbuf[i];
                    switch (ch)
                    {
                    case 0x0a: // LF
                        seenCR = false;
                        this.on_line(rePos < fileInitialLength, new String(lineBuf.toByteArray(), this.charset));
                        lineBuf.reset();
                        rePos = pos + i + 1;
                        break;
                    case 0x0d: // CR
                        if (seenCR)
                        {
                            lineBuf.write(0x0d);
                        }
                        seenCR = true;
                        break;
                    default:
                        if (seenCR)
                        {
                            seenCR = false;
                            this.on_line(rePos < fileInitialLength, new String(lineBuf.toByteArray(), this.charset));
                            lineBuf.reset();
                            rePos = pos + i + 1;
                        }
                        lineBuf.write(ch);
                    }
                }
                pos = reader.getFilePointer();
            }
            reader.seek(rePos);
            this.on_eof();
            return rePos;
        }
    }

}
