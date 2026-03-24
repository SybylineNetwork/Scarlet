package net.sybyline.scarlet.server.discord;

import moe.kyokobot.libdave.NativeDaveFactory;
import moe.kyokobot.libdave.jda.LDJDADaveSessionFactory;
import net.dv8tion.jda.api.audio.dave.DaveSession;
import net.dv8tion.jda.api.audio.dave.DaveSessionFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DAVE Session Factory implementation using libdave-jvm.
 * This provides proper E2EE support for Discord voice connections.
 */
public class LDaveSessionFactory implements DaveSessionFactory {
    
    private static final Logger LOG = LoggerFactory.getLogger(LDaveSessionFactory.class);
    private static final LDaveSessionFactory INSTANCE = createFactory();
    
    private final LDJDADaveSessionFactory delegate;
    
    private LDaveSessionFactory(LDJDADaveSessionFactory delegate) {
        this.delegate = delegate;
    }
    
    private static LDaveSessionFactory createFactory() {
        try {
            // Ensure native library is available
            NativeDaveFactory.ensureAvailable();
            LOG.info("DAVE native library loaded successfully");
            
            // Create the delegate factory
            NativeDaveFactory nativeFactory = new NativeDaveFactory();
            LDJDADaveSessionFactory jdaFactory = new LDJDADaveSessionFactory(nativeFactory);
            
            return new LDaveSessionFactory(jdaFactory);
        } catch (Throwable t) {
            LOG.error("Failed to initialize DAVE native library - E2EE will not be available", t);
            return null;
        }
    }
    
    /**
     * Get the singleton instance of the DAVE session factory.
     * @return the factory instance, or null if native library failed to load
     */
    public static LDaveSessionFactory getInstance() {
        return INSTANCE;
    }
    
    /**
     * Check if the DAVE native library is available.
     * @return true if DAVE is available, false otherwise
     */
    public static boolean isAvailable() {
        return INSTANCE != null;
    }
    
    @Override
    @NotNull
    public DaveSession createDaveSession(@NotNull net.dv8tion.jda.api.audio.dave.DaveProtocolCallbacks callbacks, long userId, long channelId) {
        return delegate.createDaveSession(callbacks, userId, channelId);
    }
}