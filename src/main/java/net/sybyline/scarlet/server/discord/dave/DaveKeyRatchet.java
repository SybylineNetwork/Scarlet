package net.sybyline.scarlet.server.discord.dave;

import java.io.Closeable;

public class DaveKeyRatchet implements Closeable
{

    public DaveKeyRatchet(DaveLibrary.DAVESessionHandle handle, String userId)
    {
        this(DaveLibrary.INSTANCE.daveSessionGetKeyRatchet(handle, userId));
    }

    public DaveKeyRatchet(DaveLibrary.DAVEKeyRatchetHandle handle)
    {
        this.handle = handle;
    }

    final DaveLibrary.DAVEKeyRatchetHandle handle;

    public boolean _isHandleValid()
    {
        return Dave.isHandleValid(this.handle);
    }

    @Override
    public void close()
    {
        DaveLibrary.INSTANCE.daveKeyRatchetDestroy(this.handle);
    }

}
