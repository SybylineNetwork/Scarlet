package net.sybyline.scarlet.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

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

    public static NoSuchMethodError noSuchCaller(boolean named)
    {
        NoSuchMethodError nsme = new NoSuchMethodError();
        StackTraceElement st[] = nsme.getStackTrace();
        /*
         * st[0]: "net.sybyline.scarlet.util.Throwables.noSuchCaller(Throwables.java:49)"
         * st[1]: The intended user
         * st[2]: The first
         * ...
         */
        if (named)
            nsme = new NoSuchMethodError(String.format("%s.%s", st[1].getClassName(), st[1].getMethodName()));
        nsme.setStackTrace(Arrays.copyOfRange(st, 2, st.length));
        return nsme;
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
