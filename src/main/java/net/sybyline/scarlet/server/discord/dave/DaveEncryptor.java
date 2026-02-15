package net.sybyline.scarlet.server.discord.dave;

import java.io.Closeable;
import java.nio.ByteBuffer;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;

public class DaveEncryptor implements Closeable
{

    public DaveEncryptor()
    {
        this(DaveLibrary.INSTANCE.daveEncryptorCreate());
    }

    public DaveEncryptor(DaveLibrary.DAVEEncryptorHandle handle)
    {
        this.handle = handle;
    }

    final DaveLibrary.DAVEEncryptorHandle handle;
    DaveLibrary.DAVEEncryptorProtocolVersionChangedCallback _callback;

    public boolean _isHandleValid()
    {
        return Dave.isHandleValid(this.handle);
    }

    @Override
    public void close()
    {
        DaveLibrary.INSTANCE.daveEncryptorDestroy(this.handle);
    }

    public void setKeyRatchet(DaveKeyRatchet keyRatchet)
    {
        DaveLibrary.INSTANCE.daveEncryptorSetKeyRatchet(this.handle, keyRatchet.handle);
    }

    public void assignSsrcToCodec(int ssrc, int codecType)
    {
        DaveLibrary.INSTANCE.daveEncryptorAssignSsrcToCodec(this.handle, ssrc, codecType);
    }

    public short getProtocolVersion()
    {
        return DaveLibrary.INSTANCE.daveEncryptorGetProtocolVersion(this.handle);
    }

    public void setPassthroughMode(boolean passthroughMode)
    {
        DaveLibrary.INSTANCE.daveEncryptorSetPassthroughMode(this.handle, passthroughMode);
    }

    public long getMaxCiphertextByteSize(int mediaType, long frameSize)
    {
        return DaveLibrary.INSTANCE.daveEncryptorGetMaxCiphertextByteSize(this.handle, mediaType, frameSize);
    }

    public boolean hasKeyRatchet()
    {
        return DaveLibrary.INSTANCE.daveEncryptorHasKeyRatchet(this.handle);
    }

    public boolean isPassthroughMode()
    {
        return DaveLibrary.INSTANCE.daveEncryptorIsPassthroughMode(this.handle);
    }

    public int encrypt(int mediaType, int ssrc, Pointer frame, long frameLength, Pointer encryptedFrame, long encryptedFrameCapacity, LongByReference bytesWritten)
    {
        return DaveLibrary.INSTANCE.daveEncryptorEncrypt(this.handle, mediaType, ssrc, frame, frameLength, encryptedFrame, encryptedFrameCapacity, bytesWritten);
    }
    public int encrypt(int mediaType, int ssrc, ByteBuffer frame, long frameLength, ByteBuffer encryptedFrame, long encryptedFrameCapacity, LongByReference bytesWritten)
    {
        return DaveLibrary.INSTANCE.daveEncryptorEncrypt(this.handle, mediaType, ssrc, frame, frameLength, encryptedFrame, encryptedFrameCapacity, bytesWritten);
    }
    public int encrypt(int mediaType, int ssrc, ByteBuffer frame, ByteBuffer encryptedFrame, LongByReference bytesWritten)
    { return this.encrypt(mediaType, ssrc, frame, frame.remaining(), encryptedFrame, encryptedFrame.remaining(), bytesWritten); }

    public void setProtocolVersionChangedCallback(DaveLibrary.DAVEEncryptorProtocolVersionChangedCallback callback)
    {
        this._callback = callback;
        DaveLibrary.INSTANCE.daveEncryptorSetProtocolVersionChangedCallback(this.handle, callback);
    }

    public DaveLibrary.DAVEEncryptorStats getStats(int mediaType)
    {
        DaveLibrary.DAVEEncryptorStats.ByReference stats = new DaveLibrary.DAVEEncryptorStats.ByReference();
        DaveLibrary.INSTANCE.daveEncryptorGetStats(this.handle, mediaType, stats);
        return stats;
    }

    public boolean _prepareTransition(DaveSessionInstance session, short version, long userId)
    {
        boolean disabled = version == DaveLibrary.DAVE_DISABLED_PROTOCOL_VERSION;
        if (!disabled)
        try (DaveKeyRatchet keyRatchet = session.getKeyRatchet(Long.toUnsignedString(userId)))
        {
            this.setKeyRatchet(keyRatchet);
            disabled = keyRatchet == null || keyRatchet.handle == null || Pointer.nativeValue(keyRatchet.handle.getPointer()) == 0L;
        }
        return disabled;
    }

    public void _processTransition(DaveSessionInstance session, short version, long userId)
    {
        boolean disabled = version == DaveLibrary.DAVE_DISABLED_PROTOCOL_VERSION;
        if (!disabled)
        try (DaveKeyRatchet keyRatchet = session.getKeyRatchet(Long.toUnsignedString(userId)))
        {
            this.setKeyRatchet(keyRatchet);
        }
        this.setPassthroughMode(this._prepareTransition(session, version, userId));
    }

}
