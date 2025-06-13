package org.cef.browser;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefLoadHandler.ErrorCode;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

public class BytesRes
{

    public static BytesRes internalServerError(String text, Charset charset, String mimeType)
    { return of(text, charset, ErrorCode.ERR_FAILED, 500, "Internal server error", mimeType); }
    public static BytesRes internalServerError(byte[] bytes, String mimeType)
    { return of(bytes, ErrorCode.ERR_FAILED, 500, "Internal server error", mimeType); }

    public static BytesRes notFound(String text, Charset charset, String mimeType)
    { return of(text, charset, ErrorCode.ERR_FILE_NOT_FOUND, 404, "Not Found", mimeType); }
    public static BytesRes notFound(byte[] bytes, String mimeType)
    { return of(bytes, ErrorCode.ERR_FILE_NOT_FOUND, 404, "Not Found", mimeType); }

    public static BytesRes ok(String text, Charset charset, String mimeType)
    { return of(text, charset, ErrorCode.ERR_NONE, 200, "OK", mimeType); }
    public static BytesRes ok(byte[] bytes, String mimeType)
    { return of(bytes, ErrorCode.ERR_NONE, 200, "OK", mimeType); }

    public static BytesRes of(String text, Charset charset, ErrorCode errorCode, int status, String statusText, String mimeType)
    {
        if (charset == null)
            charset = StandardCharsets.UTF_8;
        return of(text.getBytes(charset), errorCode, status, statusText, mimeType+"; charset="+charset.name());
    }
    public static BytesRes of(byte[] bytes, ErrorCode errorCode, int status, String statusText, String mimeType)
    {
        return new BytesRes(bytes, errorCode, status, statusText, mimeType);
    }

    BytesRes(byte[] bytes, ErrorCode errorCode, int status, String statusText, String mimeType)
    {
        this.bytes = bytes;
        this.errorCode = errorCode;
        this.status = status;
        this.statusText = statusText;
        this.mimeType = mimeType;
    }

    final byte[] bytes;
    final ErrorCode errorCode;
    final int status;
    final String statusText, mimeType;

    public CefResourceHandler newHandler()
    {
        return new Handler();
    }

    class Handler implements CefResourceHandler
    {
        Handler()
        {
        }
        private int start;
        public boolean processRequest(CefRequest request, CefCallback callback)
        {
            this.start = 0;
            callback.Continue();
            return true;
        }
        public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl)
        {
            responseLength.set(BytesRes.this.bytes.length);
            if (BytesRes.this.errorCode != null)
                response.setError(BytesRes.this.errorCode);
            response.setStatus(BytesRes.this.status);
            if (BytesRes.this.statusText != null)
                response.setStatusText(BytesRes.this.statusText);
            response.setMimeType(BytesRes.this.mimeType);
        }
        public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback)
        {
            if (this.start >= bytesToRead)
                return false;
            int end = Math.min(this.start + bytesToRead, BytesRes.this.bytes.length),
                len = end - this.start;
            System.arraycopy(BytesRes.this.bytes, this.start, dataOut, 0, len);
            bytesRead.set(len);
            this.start = end;
            return true;
        }
        public void cancel()
        {
            this.start = 0;
        }
    }

}