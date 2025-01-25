package net.sybyline.scarlet.log;

import java.io.PrintStream;
import java.time.LocalDateTime;

import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;

public class ScarletLogger extends LegacyAbstractLogger
{

    private static final long serialVersionUID = -875763706664943747L;

    public ScarletLogger()
    {
        this("");
    }

    public ScarletLogger(String name)
    {
        this.name = name;
    }

    @Override
    public boolean isTraceEnabled()
    {
        return false;
    }

    @Override
    public boolean isDebugEnabled()
    {
        return false;
    }

    @Override
    public boolean isInfoEnabled()
    {
        return true;
    }

    @Override
    public boolean isWarnEnabled()
    {
        return true;
    }

    @Override
    public boolean isErrorEnabled()
    {
        return true;
    }

    @Override
    protected String getFullyQualifiedCallerName()
    {
        return null;
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable)
    {
        LocalDateTime now = LocalDateTime.now();
        FormattingTuple result = MessageFormatter.arrayFormat(messagePattern, arguments);
        PrintStream stream = level == Level.ERROR ? System.err : System.out;
        String name = marker == null ? this.name : (this.name + "/" + marker.getName());
        String logMessage = String.format("%1$tF %1$tT [%2$s] [%3$s] %4$s%n", now, level, name, result.getMessage());
        
        stream.print(logMessage);
        if (throwable != null)
            throwable.printStackTrace(stream);
    }

}
