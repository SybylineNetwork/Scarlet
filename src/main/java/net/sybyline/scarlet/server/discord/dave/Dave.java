package net.sybyline.scarlet.server.discord.dave;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import com.sun.jna.Pointer;

public enum Dave
{

    INSTANCE(),
    ;

    DaveLibrary.DAVELogSinkCallback _callback;

    public short maxSupportedProtocolVersion()
    {
        return DaveLibrary.INSTANCE.daveMaxSupportedProtocolVersion();
    }

    public void free(Pointer ptr)
    {
        DaveLibrary.INSTANCE.daveFree(ptr);
    }

    public DaveSessionInstance createSession(Pointer context, String authSessionId, DaveLibrary.DAVEMLSFailureCallback callback, Pointer userData)
    {
        return new DaveSessionInstance(userData, authSessionId, callback, userData);
    }

    public DaveEncryptor createEncryptor()
    {
        return new DaveEncryptor();
    }

    public DaveDecryptor createDecryptor()
    {
        return new DaveDecryptor();
    }

    public void daveSetLogSinkCallback(DaveLibrary.DAVELogSinkCallback callback)
    {
        this._callback = callback;
        DaveLibrary.INSTANCE.daveSetLogSinkCallback(callback);
    }

    public void daveSetLogSinkCallbackDefault()
    {
        this.daveSetLogSinkCallback(Dave::defaultLog);
    }

    static boolean isHandleValid(DaveLibrary.OpaqueHandle handle)
    {
        return handle != null && Pointer.nativeValue(handle.getPointer()) != 0L;
    }

    static final Logger LOG = LoggerFactory.getLogger("DAVE");

    static void defaultLog(int severity, String file, int line, String message)
    {
        Level level;
        switch (severity)
        {
        default:
        case DaveLibrary.DAVE_LOGGING_SEVERITY_NONE:    return;
        case DaveLibrary.DAVE_LOGGING_SEVERITY_VERBOSE: level = Level.DEBUG; break;
        case DaveLibrary.DAVE_LOGGING_SEVERITY_INFO:    level = Level. INFO; break;
        case DaveLibrary.DAVE_LOGGING_SEVERITY_WARNING: level = Level. WARN; break;
        case DaveLibrary.DAVE_LOGGING_SEVERITY_ERROR:   level = Level.ERROR; break;
        }
        if (!LOG.isEnabledForLevel(level))
            return;
        String string = String.format("%s:%s %s",
            file.substring(Math.max(file.lastIndexOf('\\'), file.lastIndexOf('/')) + 1),
            line,
            message
        );
        switch (level)
        {
        default:
        case TRACE: LOG.trace(string); break;
        case DEBUG: LOG.debug(string); break;
        case  INFO: LOG. info(string); break;
        case  WARN: LOG. warn(string); break;
        case ERROR: LOG.error(string); break;
        }
    }

}
