package org.cef.browser;

import java.io.InputStream;
import java.util.concurrent.Callable;

import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandler;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

public class CefStreamResourceHandler implements CefResourceHandler
{

    public CefStreamResourceHandler(Callable<InputStream> callable)
    {
        this.callable = callable;
        this.in = null;
    }

    final Callable<InputStream> callable;
    InputStream in;

    @Override
    public boolean processRequest(CefRequest request, CefCallback callback)
    {
        try
        {
            this.in = this.callable.call();
            callback.Continue();
        }
        catch (Exception ex)
        {
            callback.cancel();
        }
        return true;
    }

    @Override
    public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl)
    {
        responseLength.set(-1);
    }

    @Override
    public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback)
    {
        InputStream in = this.in;
        if (in == null)
        {
            bytesRead.set(0);
            return false;
        }
        int read;
        try
        {
            read = in.read(dataOut, 0, bytesToRead);
        }
        catch (Exception ex)
        {
            this.cancel();
            return false;
        }
        if (read == -1)
        {
            this.cancel();
            bytesRead.set(0);
            return false;
        }
        bytesRead.set(read);
        return true;
    }

    @Override
    public void cancel()
    {
        if (this.in != null) try
        {
            this.in.close();
        }
        catch (Exception ex)
        {
        }
        finally
        {
            this.in = null;
        }
    }

}
