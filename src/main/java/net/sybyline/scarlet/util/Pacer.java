package net.sybyline.scarlet.util;

public class Pacer
{

    public Pacer(long millis)
    {
        if (millis < 1L)
            millis = 1L;
        this.millis = millis;
        this.epoch = System.currentTimeMillis() - millis;
    }

    private final long millis;
    private long epoch;

    public synchronized boolean await()
    {
        boolean status = !Thread.interrupted();
        long now = System.currentTimeMillis();
        try
        {
            for (long layover, next = this.epoch + this.millis; (layover = next - now) > 0; now = System.currentTimeMillis())
                Thread.sleep(layover);
            return status;
        }
        catch (InterruptedException iex)
        {
            Thread.currentThread().interrupt();
            return false;
        }
        finally
        {
            this.epoch = now;
        }
    }

}