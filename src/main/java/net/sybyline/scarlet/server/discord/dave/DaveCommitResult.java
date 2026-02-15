package net.sybyline.scarlet.server.discord.dave;

import java.io.Closeable;
import java.nio.ByteBuffer;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

public class DaveCommitResult implements Closeable
{

    public DaveCommitResult(DaveLibrary.DAVESessionHandle handle, Pointer commit, long length)
    {
        this(DaveLibrary.INSTANCE.daveSessionProcessCommit(handle, commit, length));
    }
    public DaveCommitResult(DaveLibrary.DAVESessionHandle handle, ByteBuffer commit, long length)
    {
        this(DaveLibrary.INSTANCE.daveSessionProcessCommit(handle, commit, length));
    }
    public DaveCommitResult(DaveLibrary.DAVESessionHandle handle, ByteBuffer commit)
    { this(handle, commit, commit.remaining()); }

    public DaveCommitResult(DaveLibrary.DAVECommitResultHandle handle)
    {
        this.handle = handle;
    }

    final DaveLibrary.DAVECommitResultHandle handle;

    public boolean _isHandleValid()
    {
        return Dave.isHandleValid(this.handle);
    }

    public boolean isFailed()
    {
        return DaveLibrary.INSTANCE.daveCommitResultIsFailed(this.handle);
    }

    public boolean isIgnored()
    {
        return DaveLibrary.INSTANCE.daveCommitResultIsIgnored(this.handle);
    }

    public long[] getRosterMemberIds()
    {
        PointerByReference rosterIds = new PointerByReference();
        LongByReference rosterIdsLength = new LongByReference();
        DaveLibrary.INSTANCE.daveCommitResultGetRosterMemberIds(this.handle, rosterIds, rosterIdsLength);
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

    public byte[] getRosterMemberSignature(long rosterId)
    {
        PointerByReference signature = new PointerByReference();
        LongByReference signatureLength = new LongByReference();
        DaveLibrary.INSTANCE.daveCommitResultGetRosterMemberSignature(this.handle, rosterId, signature, signatureLength);
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
        DaveLibrary.INSTANCE.daveCommitResultDestroy(this.handle);
    }

}
