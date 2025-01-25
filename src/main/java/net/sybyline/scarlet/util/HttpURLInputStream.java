package net.sybyline.scarlet.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import net.sybyline.scarlet.Scarlet;

public class HttpURLInputStream extends FilterInputStream
{

    public static HttpURLInputStream get(String url) throws IOException
    {
        URL url0 = new URL(url);
        HttpURLConnection connection = (HttpURLConnection)url0.openConnection();
        connection.setRequestProperty("User-Agent", Scarlet.USER_AGENT_STATIC);
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(5_000);
        try
        {
            return new HttpURLInputStream(connection);
        }
        catch (IOException ioex)
        {
            connection.disconnect();
            throw ioex;
        }
    }

    protected HttpURLInputStream(HttpURLConnection connection) throws IOException
    {
        super(connection.getInputStream());
        this.connection = connection;
    }

    private final HttpURLConnection connection;

    @Override
    public void close() throws IOException
    {
        try
        {
            super.close();
        }
        finally
        {
            this.connection.disconnect();
        }
    }

}
