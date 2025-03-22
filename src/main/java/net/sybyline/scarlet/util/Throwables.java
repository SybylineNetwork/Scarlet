package net.sybyline.scarlet.util;

import java.io.PrintWriter;
import java.io.StringWriter;

public interface Throwables
{

    static <T> T noreturn(Throwable throwable)
    {
        throw ThrowablesUtil.rethrow(throwable, false);
    }

    static <T> T noreturnCause(Throwable throwable)
    {
        throw ThrowablesUtil.rethrow(throwable, true);
    }

    static <X extends Throwable> RuntimeException yeet(Throwable throwable)
    {
        throw ThrowablesUtil.rethrow(throwable, false);
    }

    static <X extends Throwable> RuntimeException yeetCause(Throwable throwable)
    {
        throw ThrowablesUtil.rethrow(throwable, true);
    }

    static <E extends Throwable, T> T err(E t) throws E
    {
        throw t;
    }

    static <T> T unimplemented(String msg)
    {
        throw new UnsupportedOperationException("Feature unimplemented: " + msg);
    }

    static String getString(Throwable throwable)
    {
        StringWriter ret = new StringWriter();
        throwable.printStackTrace(new PrintWriter(ret));
        return ret.toString();
    }

}

interface ThrowablesUtil
{
    @SuppressWarnings("unchecked")
    static <X extends Throwable> RuntimeException rethrow(Throwable throwable, boolean cause) throws X
    {
        throw (X)(cause && throwable.getCause() != null ? throwable.getCause() : throwable);
    }
}
