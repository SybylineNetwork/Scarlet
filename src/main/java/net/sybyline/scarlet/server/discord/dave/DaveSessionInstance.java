package net.sybyline.scarlet.server.discord.dave;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import com.sun.jna.Pointer;
import com.sun.jna.StringArray;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;


public class DaveSessionInstance implements Closeable
{

    public DaveSessionInstance(Pointer context, String authSessionId, DaveLibrary.DAVEMLSFailureCallback callback, Pointer userData)
    {
        this(DaveLibrary.INSTANCE.daveSessionCreate(context, authSessionId, callback, userData));
        this._callback = callback;
    }

    public DaveSessionInstance(DaveLibrary.DAVESessionHandle handle)
    {
        this.handle = handle;
    }

    final DaveLibrary.DAVESessionHandle handle;
    DaveLibrary.DAVEMLSFailureCallback _callback;

    public boolean _isHandleValid()
    {
        return Dave.isHandleValid(this.handle);
    }

    @Override
    public void close()
    {
        DaveLibrary.INSTANCE.daveSessionDestroy(this.handle);
    }

    public void init(short version, long groupId, String selfUserId)
    {
        DaveLibrary.INSTANCE.daveSessionInit(this.handle, version, groupId, selfUserId);
    }

    public void reset()
    {
        DaveLibrary.INSTANCE.daveSessionReset(this.handle);
    }

    public void setProtocolVersion(short version)
    {
        DaveLibrary.INSTANCE.daveSessionSetProtocolVersion(this.handle, version);
    }

    public short getProtocolVersion()
    {
        return DaveLibrary.INSTANCE.daveSessionGetProtocolVersion(this.handle);
    }

    public byte[] getLastEpochAuthenticator()
    {
        PointerByReference authenticator = new PointerByReference();
        LongByReference length = new LongByReference();
        DaveLibrary.INSTANCE.daveSessionGetLastEpochAuthenticator(this.handle, authenticator, length);
        Pointer authenticatorBuffer = authenticator.getValue();
        try
        {
            return authenticatorBuffer.getByteArray(0L, Math.toIntExact(length.getValue()));
        }
        finally
        {
            DaveLibrary.INSTANCE.daveFree(authenticatorBuffer);
        }
    }

    public void setExternalSender(Pointer externalSender, long length)
    {
        DaveLibrary.INSTANCE.daveSessionSetExternalSender(this.handle, externalSender, length);
    }
    public void setExternalSender(ByteBuffer externalSender, long length)
    {
        DaveLibrary.INSTANCE.daveSessionSetExternalSender(this.handle, externalSender, length);
    }
    public void setExternalSender(ByteBuffer externalSender)
    { this.setExternalSender(externalSender, externalSender.remaining()); }

    public byte[] processProposals(Pointer proposals, long length, StringArray recognizedUserIds, long recognizedUserIdsLength)
    {
        PointerByReference commitWelcomeBytes = new PointerByReference();
        LongByReference commitWelcomeBytesLength = new LongByReference();
        DaveLibrary.INSTANCE.daveSessionProcessProposals(handle, proposals, length, recognizedUserIds, recognizedUserIdsLength, commitWelcomeBytes, commitWelcomeBytesLength);
        Pointer commitWelcomeBytesBuffer = commitWelcomeBytes.getValue();
        try
        {
            return commitWelcomeBytesBuffer.getByteArray(0L, Math.toIntExact(commitWelcomeBytesLength.getValue()));
        }
        finally
        {
            DaveLibrary.INSTANCE.daveFree(commitWelcomeBytesBuffer);
        }
    }
    public byte[] processProposals(Pointer proposals, long length, String... recognizedUserIds)
    { return this.processProposals(proposals, length, new StringArray(recognizedUserIds), recognizedUserIds.length); }
    public byte[] processProposals(ByteBuffer proposals, long length, StringArray recognizedUserIds, long recognizedUserIdsLength)
    {
        PointerByReference commitWelcomeBytes = new PointerByReference();
        LongByReference commitWelcomeBytesLength = new LongByReference();
        DaveLibrary.INSTANCE.daveSessionProcessProposals(handle, proposals, length, recognizedUserIds, recognizedUserIdsLength, commitWelcomeBytes, commitWelcomeBytesLength);
        Pointer commitWelcomeBytesBuffer = commitWelcomeBytes.getValue();
        try
        {
            return commitWelcomeBytesBuffer.getByteArray(0L, Math.toIntExact(commitWelcomeBytesLength.getValue()));
        }
        finally
        {
            DaveLibrary.INSTANCE.daveFree(commitWelcomeBytesBuffer);
        }
    }
    public byte[] processProposals(ByteBuffer proposals, StringArray recognizedUserIds, long recognizedUserIdsLength)
    { return this.processProposals(proposals, proposals.remaining(), recognizedUserIds, recognizedUserIdsLength); }
    public byte[] processProposals(ByteBuffer proposals, long length, String... recognizedUserIds)
    { return this.processProposals(proposals, length, new StringArray(recognizedUserIds), recognizedUserIds.length); }
    public byte[] processProposals(ByteBuffer proposals, String... recognizedUserIds)
    { return this.processProposals(proposals, proposals.remaining(), new StringArray(recognizedUserIds), recognizedUserIds.length); }
    public void processProposals(ByteBuffer proposals, String[] recognizedUserIds, Consumer<ByteBuffer> consumer)
    { 
        PointerByReference commitWelcomeBytes = new PointerByReference();
        LongByReference commitWelcomeBytesLength = new LongByReference();
        DaveLibrary.INSTANCE.daveSessionProcessProposals(this.handle, proposals, proposals.remaining(), new StringArray(recognizedUserIds), recognizedUserIds.length, commitWelcomeBytes, commitWelcomeBytesLength);
        Pointer commitWelcomeBytesBuffer = commitWelcomeBytes.getValue();
        try
        {
            consumer.accept(commitWelcomeBytesBuffer.getByteBuffer(0L, commitWelcomeBytesLength.getValue()));
        }
        finally
        {
            DaveLibrary.INSTANCE.daveFree(commitWelcomeBytesBuffer);
        }
    }

    public DaveCommitResult processCommit(Pointer commit, long length)
    {
        return new DaveCommitResult(this.handle, commit, length);
    }
    public DaveCommitResult processCommit(ByteBuffer commit, long length)
    {
        return new DaveCommitResult(this.handle, commit, length);
    }
    public DaveCommitResult processCommit(ByteBuffer commit)
    { return this.processCommit(commit, commit.remaining()); }

    public DaveWelcomeResult processWelcome(Pointer welcome, long length, StringArray recognizedUserIds, long recognizedUserIdsLength)
    {
        return new DaveWelcomeResult(this.handle, welcome, length, recognizedUserIds, recognizedUserIdsLength);
    }
    public DaveWelcomeResult processWelcome(Pointer welcome, long length, String... recognizedUserIds)
    { return this.processWelcome(welcome, length, new StringArray(recognizedUserIds), recognizedUserIds.length); }
    public DaveWelcomeResult processWelcome(ByteBuffer welcome, long length, StringArray recognizedUserIds, long recognizedUserIdsLength)
    {
        return new DaveWelcomeResult(this.handle, welcome, length, recognizedUserIds, recognizedUserIdsLength);
    }
    public DaveWelcomeResult processWelcome(ByteBuffer welcome, long length, String... recognizedUserIds)
    { return this.processWelcome(welcome, length, new StringArray(recognizedUserIds), recognizedUserIds.length); }
    public DaveWelcomeResult processWelcome(ByteBuffer welcome, StringArray recognizedUserIds, long recognizedUserIdsLength)
    { return this.processWelcome(welcome, welcome.remaining(), recognizedUserIds, recognizedUserIdsLength); }
    public DaveWelcomeResult processWelcome(ByteBuffer welcome, String... recognizedUserIds)
    { return this.processWelcome(welcome, welcome.remaining(), new StringArray(recognizedUserIds), recognizedUserIds.length); }

    public byte[] getMarshalledKeyPackage()
    {
        PointerByReference keyPackage = new PointerByReference();
        LongByReference length = new LongByReference();
        DaveLibrary.INSTANCE.daveSessionGetMarshalledKeyPackage(this.handle, keyPackage, length);
        Pointer keyPackageBuffer = keyPackage.getValue();
        try
        {
            return keyPackageBuffer.getByteArray(0L, Math.toIntExact(length.getValue()));
        }
        finally
        {
            DaveLibrary.INSTANCE.daveFree(keyPackageBuffer);
        }
    }
    public void getMarshalledKeyPackage(Consumer<ByteBuffer> consumer)
    {
        PointerByReference keyPackage = new PointerByReference();
        LongByReference length = new LongByReference();
        DaveLibrary.INSTANCE.daveSessionGetMarshalledKeyPackage(this.handle, keyPackage, length);
        Pointer keyPackageBuffer = keyPackage.getValue();
        try
        {
            consumer.accept(keyPackageBuffer.getByteBuffer(0L, length.getValue()));
        }
        finally
        {
            DaveLibrary.INSTANCE.daveFree(keyPackageBuffer);
        }
    }

    public DaveKeyRatchet getKeyRatchet(String userId)
    {
        return new DaveKeyRatchet(this.handle, userId);
    }

    public void getPairwiseFingerprint(short version, String userId, DaveLibrary.DAVEPairwiseFingerprintCallback callback, Pointer userData)
    {
        DaveLibrary.INSTANCE.daveSessionGetPairwiseFingerprint(this.handle, version, userId, callback, userData);
    }

}
