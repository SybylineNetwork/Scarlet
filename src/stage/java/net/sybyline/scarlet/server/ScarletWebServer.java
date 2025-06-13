package net.sybyline.scarlet.server;

import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;
import org.nanohttpd.protocols.websockets.NanoWSD;
import org.nanohttpd.protocols.websockets.WebSocket;

import net.sybyline.scarlet.util.VrcIds;

public class ScarletWebServer extends NanoWSD
{

    public static final Pattern WS_REQ_PATH = Pattern.compile("/scarlet/ws/(?<id>"+VrcIds.P_ID_USER+")/(?<token>[-_a-zA-Z0-9]+)");

    public ScarletWebServer(String hostname, int port)
    {
        super(hostname, port);
    }
    public ScarletWebServer(int port)
    {
        super(port);
    }

    @Override
    public final Response handleWebSocket(IHTTPSession session)
    {
        Map<String, String> headers = session.getHeaders();
        
        if (!this.isWebsocketRequested(session))
            return null;
        
        if (!"13".equalsIgnoreCase((String)headers.get("sec-websocket-version")))
            return Response.newFixedLengthResponse(Status.BAD_REQUEST, "text/plain","Invalid Websocket-Version "+headers.get("sec-websocket-version"));
        
        if (!headers.containsKey("sec-websocket-key"))
            return Response.newFixedLengthResponse(Status.BAD_REQUEST, "text/plain", "Missing Websocket-Key");
        
        WebSocket webSocket = this.openWebSocket(session);
        if (webSocket == null)
            return null;
        
        Response handshakeResponse = webSocket.getHandshakeResponse();
        
        try
        {
            handshakeResponse.addHeader("sec-websocket-accept", makeAcceptKey((String)headers.get("sec-websocket-key")));
        }
        catch (NoSuchAlgorithmException nsaex)
        {
            return Response.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", "The SHA-1 Algorithm required for websockets is not available on the server.");
        }
        
        if (headers.containsKey("sec-websocket-protocol"))
            handshakeResponse.addHeader("sec-websocket-protocol", ((String)headers.get("sec-websocket-protocol")).split(",")[0]);
        
        return handshakeResponse;
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake)
    {
        String host = handshake.getHeaders().get("host");
        Matcher m = WS_REQ_PATH.matcher(handshake.getUri());
        if (m.matches())
        {
            String id = m.group("id"),
                   token = m.group("token");
        }
        return null;
    }

}
