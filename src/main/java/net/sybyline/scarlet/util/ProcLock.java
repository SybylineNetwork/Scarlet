package net.sybyline.scarlet.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProcLock
{

    @SuppressWarnings("resource")
    public static boolean tryLock(File file)
    {
        try
        {
            FileChannel channel = new RandomAccessFile(file, "rw").getChannel();
            FileLock lock = channel.tryLock();
            if (lock == null)
            {
                channel.close();
                throw new IOException();
            }
            locks.add(() ->
            {
                MiscUtils.close(lock);
                MiscUtils.close(channel);
                file.delete();
            });
            return true;
        }
        catch (Exception ex)
        {
            return false;
        }
    }

    private static final List<Runnable> locks = Collections.synchronizedList(new ArrayList<>());
    static
    {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> locks.forEach(Runnable::run), "ProcLock.shutdownHook"));
    }

}
