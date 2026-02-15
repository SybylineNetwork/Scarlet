package net.sybyline.scarlet.server.discord.dave;

import java.io.Closeable;
import java.nio.ByteBuffer;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;

public class DaveDecryptor implements Closeable
{

    public DaveDecryptor()
    {
        this(DaveLibrary.INSTANCE.daveDecryptorCreate());
    }

    public DaveDecryptor(DaveLibrary.DAVEDecryptorHandle handle)
    {
        this.handle = handle;
    }

    final DaveLibrary.DAVEDecryptorHandle handle;

    public boolean _isHandleValid()
    {
        return Dave.isHandleValid(this.handle);
    }

    @Override
    public void close()
    {
        DaveLibrary.INSTANCE.daveDecryptorDestroy(this.handle);
    }

    public void transitionToKeyRatchet(DaveKeyRatchet keyRatchet)
    {
        DaveLibrary.INSTANCE.daveDecryptorTransitionToKeyRatchet(this.handle, keyRatchet.handle);
    }

    public void transitionToPassthroughMode(boolean passthroughMode)
    {
        DaveLibrary.INSTANCE.daveDecryptorTransitionToPassthroughMode(this.handle, passthroughMode);
    }

    public int decrypt(int mediaType, Pointer encryptedFrame, long encryptedFrameLength, Pointer frame, long frameCapacity, LongByReference bytesWritten)
    {
        return DaveLibrary.INSTANCE.daveDecryptorDecrypt(this.handle, mediaType, encryptedFrame, encryptedFrameLength, frame, frameCapacity, bytesWritten);
    }
    public int decrypt(int mediaType, ByteBuffer encryptedFrame, long encryptedFrameLength, ByteBuffer frame, long frameCapacity, LongByReference bytesWritten)
    {
        return DaveLibrary.INSTANCE.daveDecryptorDecrypt(this.handle, mediaType, encryptedFrame, encryptedFrameLength, frame, frameCapacity, bytesWritten);
    }
    public int decrypt(int mediaType, ByteBuffer encryptedFrame, ByteBuffer frame, LongByReference bytesWritten)
    { return this.decrypt(mediaType, encryptedFrame, encryptedFrame.remaining(), frame, frame.remaining(), bytesWritten); }

    public long getMaxPlaintextByteSize(int mediaType, long encryptedFrameSize)
    {
        return DaveLibrary.INSTANCE.daveDecryptorGetMaxPlaintextByteSize(this.handle, mediaType, encryptedFrameSize);
    }

    public DaveLibrary.DAVEDecryptorStats getStats(int mediaType)
    {
        DaveLibrary.DAVEDecryptorStats.ByReference stats = new DaveLibrary.DAVEDecryptorStats.ByReference();
        DaveLibrary.INSTANCE.daveDecryptorGetStats(this.handle, mediaType, stats);
        return stats;
    }

    public void _prepareTransition(DaveSessionInstance session, short version, long userId)
    {
        boolean disabled = version == DaveLibrary.DAVE_DISABLED_PROTOCOL_VERSION;
        if (!disabled)
        try (DaveKeyRatchet keyRatchet = session.getKeyRatchet(Long.toUnsignedString(userId)))
        {
            this.transitionToKeyRatchet(keyRatchet);
        }
        this.transitionToPassthroughMode(disabled);
    }

}
