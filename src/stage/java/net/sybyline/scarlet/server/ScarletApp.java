package net.sybyline.scarlet.server;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class ScarletApp
{

    protected ScarletApp()
    {
        this.running = true;
        this.threadidx = new AtomicInteger();
        this.exec = Executors.newScheduledThreadPool(4, runnable -> new Thread(runnable, "Scarlet Worker Thread "+this.threadidx.incrementAndGet()));
        this.execModal = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "Scarlet Modal UI Thread "+this.threadidx.incrementAndGet()));
        
    }

    protected volatile boolean running;
    protected final AtomicInteger threadidx;
    public final ScheduledExecutorService exec;
    public final ScheduledExecutorService execModal;

}
