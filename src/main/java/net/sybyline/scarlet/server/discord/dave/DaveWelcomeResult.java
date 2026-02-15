package net.sybyline.scarlet.server.discord.dave;

import java.io.Closeable;
import java.nio.ByteBuffer;

import com.sun.jna.Pointer;
import com.sun.jna.StringArray;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

public class DaveWelcomeResult implements Closeable
{

    public DaveWelcomeResult(DaveLibrary.DAVESessionHandle handle, Pointer welcome, long length, StringArray recognizedUserIds, long recognizedUserIdsLength)
    {
        this(DaveLibrary.INSTANCE.daveSessionProcessWelcome(handle, welcome, length, recognizedUserIds, recognizedUserIdsLength));
    }
    public DaveWelcomeResult(DaveLibrary.DAVESessionHandle handle, Pointer welcome, long length, String... recognizedUserIds)
    { this(handle, welcome, length, new StringArray(recognizedUserIds), recognizedUserIds.length); }
    public DaveWelcomeResult(DaveLibrary.DAVESessionHandle handle, ByteBuffer welcome, long length, StringArray recognizedUserIds, long recognizedUserIdsLength)
    {
        this(DaveLibrary.INSTANCE.daveSessionProcessWelcome(handle, welcome, length, recognizedUserIds, recognizedUserIdsLength));
    }
    public DaveWelcomeResult(DaveLibrary.DAVESessionHandle handle, ByteBuffer welcome, long length, String... recognizedUserIds)
    { this(handle, welcome, length, new StringArray(recognizedUserIds), recognizedUserIds.length); }
    public DaveWelcomeResult(DaveLibrary.DAVESessionHandle handle, ByteBuffer welcome, StringArray recognizedUserIds, long recognizedUserIdsLength)
    { this(handle, welcome, welcome.remaining(), recognizedUserIds, recognizedUserIdsLength); }
    public DaveWelcomeResult(DaveLibrary.DAVESessionHandle handle, ByteBuffer welcome, String... recognizedUserIds)
    { this(handle, welcome, welcome.remaining(), new StringArray(recognizedUserIds), recognizedUserIds.length); }

    public DaveWelcomeResult(DaveLibrary.DAVEWelcomeResultHandle handle)
    {
        this.handle = handle;
    }

    final DaveLibrary.DAVEWelcomeResultHandle handle;

    public boolean _isHandleValid()
    {
        return Dave.isHandleValid(this.handle);
    }

    public long[] getRosterMemberIds()
    {
        PointerByReference rosterIds = new PointerByReference();
        LongByReference rosterIdsLength = new LongByReference();
        DaveLibrary.INSTANCE.daveWelcomeResultGetRosterMemberIds(this.handle, rosterIds, rosterIdsLength);
        Pointer rosterIdsBuffer = rosterIds.getValue();
        try
        {
            return rosterIdsBuffer.getLongArray(0L, Math.toIntExact(rosterIdsLength.getValue()));
        }
        finally
        {
            DaveLibrary.INSTANCE.daveFree(rosterIdsBuffer);
        }
    }

    public byte[] daveWelcomeResultGetRosterMemberSignature(long rosterId)
    {
        PointerByReference signature = new PointerByReference();
        LongByReference signatureLength = new LongByReference();
        DaveLibrary.INSTANCE.daveWelcomeResultGetRosterMemberSignature(this.handle, rosterId, signature, signatureLength);
        Pointer signatureBuffer = signature.getValue();
        try
        {
            return signatureBuffer.getByteArray(0L, Math.toIntExact(signatureLength.getValue()));
        }
        finally
        {
            DaveLibrary.INSTANCE.daveFree(signatureBuffer);
        }
    }

    @Override
    public void close()
    {
        DaveLibrary.INSTANCE.daveWelcomeResultDestroy(this.handle);
    }

}
