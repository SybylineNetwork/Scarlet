package net.sybyline.scarlet.util;

public class AppInitError extends Error
{

    private static final long serialVersionUID = 7177288423495163522L;

    public AppInitError(String message, Throwable cause)
    {
        super(message, cause);
    }

    public AppInitError(String message)
    {
        super(message);
    }

    public AppInitError(Throwable cause)
    {
        super(cause);
    }

    public AppInitError()
    {
        super();
    }

}
