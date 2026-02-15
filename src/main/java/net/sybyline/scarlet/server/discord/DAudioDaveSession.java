package net.sybyline.scarlet.server.discord;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.sun.jna.ptr.LongByReference;

import net.dv8tion.jda.api.audio.dave.DaveProtocolCallbacks;
import net.dv8tion.jda.api.audio.dave.DaveSession;
import net.sybyline.scarlet.server.discord.dave.Dave;
import net.sybyline.scarlet.server.discord.dave.DaveCommitResult;
import net.sybyline.scarlet.server.discord.dave.DaveDecryptor;
import net.sybyline.scarlet.server.discord.dave.DaveEncryptor;
import net.sybyline.scarlet.server.discord.dave.DaveLibrary;
import net.sybyline.scarlet.server.discord.dave.DaveSessionInstance;
import net.sybyline.scarlet.server.discord.dave.DaveWelcomeResult;

public class DAudioDaveSession implements DaveSession, Closeable
{

    public DAudioDaveSession(DaveProtocolCallbacks callbacks, long userId, long channelId)
    {
        this.callbacks = callbacks;
        this.selfUserId = userId;
        this.channelId = channelId;
        this.session = Dave.INSTANCE.createSession(null, null, null, null);
        this.encryptor = new DaveEncryptor();
        this.decryptors = new ConcurrentHashMap<>();
        this.preparedTransitions = new ConcurrentHashMap<>();
        this.currentProtocolVersion = DaveLibrary.DAVE_DISABLED_PROTOCOL_VERSION;
        this.closed = false;
    }

    final DaveProtocolCallbacks callbacks;
    final long selfUserId, channelId;
    final DaveSessionInstance session;
    final DaveEncryptor encryptor;
    final Map<Long, DaveDecryptor> decryptors;
    final Map<Integer, Short> preparedTransitions;
    volatile short currentProtocolVersion;
    volatile boolean closed;

    @Override
    public synchronized void close()
    {
        this.closed = true;
        this.encryptor.close();
        this.decryptors.values().forEach(DaveDecryptor::close);
        this.decryptors.clear();
        this.session.close();
    }

    @Override
    public int getMaxProtocolVersion()
    {
        return Short.toUnsignedInt(Dave.INSTANCE.maxSupportedProtocolVersion());
    }

    @Override
    public synchronized int getMaxEncryptedFrameSize(MediaType type, int frameSize)
    {
        if (this.closed)
            return frameSize;
        int mediaType;
        switch (type)
        {
        case AUDIO: mediaType = DaveLibrary.DAVE_MEDIA_TYPE_AUDIO; break;
        default: return frameSize;
        }
        return Math.toIntExact(this.encryptor.getMaxCiphertextByteSize(mediaType, Integer.toUnsignedLong(frameSize)));
    }

    @Override
    public synchronized int getMaxDecryptedFrameSize(MediaType media, long userId, int frameSize)
    {
        if (this.closed)
            return frameSize;
        int mediaType;
        switch (media)
        {
        case AUDIO: mediaType = DaveLibrary.DAVE_MEDIA_TYPE_AUDIO; break;
        default: return frameSize;
        }
        DaveDecryptor decryptor = this.decryptors.get(userId);
        if (decryptor == null)
            return frameSize;
        return Math.toIntExact(decryptor.getMaxPlaintextByteSize(mediaType, Integer.toUnsignedLong(frameSize)));
    }

    @Override
    public synchronized void assignSsrcToCodec(Codec codec, int ssrc)
    {
        if (this.closed)
            return;
        int codecType;
        switch (codec)
        {
        case OPUS: codecType = DaveLibrary.DAVE_CODEC_OPUS; break;
        default: return;
        }
        this.encryptor.assignSsrcToCodec(ssrc, codecType);
    }

    @Override
    public synchronized boolean encrypt(MediaType media, int ssrc, ByteBuffer data, ByteBuffer encrypted)
    {
        if (this.closed)
            return false;
        int mediaType;
        switch (media)
        {
        case AUDIO: mediaType = DaveLibrary.DAVE_MEDIA_TYPE_AUDIO; break;
        default: return false;
        }
        LongByReference bytesWritten = new LongByReference();
        return this.encryptor.encrypt(mediaType, ssrc, data, encrypted, bytesWritten) == DaveLibrary.DAVE_ENCRYPTOR_RESULT_CODE_SUCCESS;
    }

    @Override
    public synchronized boolean decrypt(MediaType media, long userId, ByteBuffer encrypted, ByteBuffer decrypted)
    {
        if (this.closed)
            return false;
        int mediaType;
        switch (media)
        {
        case AUDIO: mediaType = DaveLibrary.DAVE_MEDIA_TYPE_AUDIO; break;
        default: return false;
        }
        DaveDecryptor decryptor = this.decryptors.get(userId);
        if (decryptor == null)
            return false;
        LongByReference bytesWritten = new LongByReference();
        return decryptor.decrypt(mediaType, encrypted, decrypted, bytesWritten) == DaveLibrary.DAVE_DECRYPTOR_RESULT_CODE_SUCCESS;
    }

    @Override
    public synchronized void addUser(long userId)
    {
        if (this.closed)
            return;
        this.decryptors.computeIfAbsent(userId, $ -> Dave.INSTANCE.createDecryptor())
            ._prepareTransition(this.session, this.currentProtocolVersion, userId);
    }

    @Override
    public synchronized void removeUser(long userId)
    {
        if (this.closed)
            return;
        DaveDecryptor decryptor = this.decryptors.remove(userId);
        if (decryptor != null)
            decryptor.close();
    }

    @Override
    public synchronized void initialize()
    {
    }

    @Override
    public synchronized void destroy()
    {
        this.close();
    }

    @Override
    public synchronized void onSelectProtocolAck(int protocolVersion)
    {
        if (this.closed)
            return;
        this.handleDaveProtocolInit((short)protocolVersion);
    }

    @Override
    public synchronized void onDaveProtocolPrepareTransition(int transitionId, int protocolVersion)
    {
        if (this.closed)
            return;
        this.prepareProtocolTransition(transitionId, (short)protocolVersion);
    }

    @Override
    public synchronized void onDaveProtocolExecuteTransition(int transitionId)
    {
        if (this.closed)
            return;
        this.executeProtocolTransition(transitionId);
    }

    @Override
    public synchronized void onDaveProtocolPrepareEpoch(long epoch, int protocolVersion)
    {
        if (this.closed)
            return;
        this.handlePrepareEpoch(epoch, (short)protocolVersion);
    }

    @Override
    public synchronized void onDaveProtocolMLSExternalSenderPackage(ByteBuffer externalSenderPackage)
    {
        if (this.closed)
            return;
        this.session.setExternalSender(externalSenderPackage);
    }

    @Override
    public synchronized void onMLSProposals(ByteBuffer proposals)
    {
        if (this.closed)
            return;
        this.session.processProposals(proposals, this.getRecognizedUserIds(), this.callbacks::sendMLSCommitWelcome);
    }

    @Override
    public synchronized void onMLSPrepareCommitTransition(int transitionId, ByteBuffer commit)
    {
        if (this.closed)
            return;
        try (DaveCommitResult result = this.session.processCommit(commit))
        {
            if (result.isIgnored())
            {
                this.preparedTransitions.remove(transitionId);
            }
            else if (result.isFailed())
            {
                this.sendInvalidCommitWelcome(transitionId);
                // The cast immediately below smells sus...
                this.handleDaveProtocolInit((short)transitionId);
            }
            else
            {
                this.prepareProtocolTransition(transitionId, this.session.getProtocolVersion());
            }
        }
    }

    @Override
    public synchronized void onMLSWelcome(int transitionId, ByteBuffer welcome)
    {
        if (this.closed)
            return;
        try (DaveWelcomeResult result = this.session.processWelcome(welcome, this.getRecognizedUserIds()))
        {
            if (result != null && result._isHandleValid())
            {
                this.prepareProtocolTransition(transitionId, this.session.getProtocolVersion());
            }
            else
            {
                this.sendInvalidCommitWelcome(transitionId);
                // The cast immediately below smells sus...
                this.handleDaveProtocolInit((short)transitionId);
            }
        }
    }

    private String[] getRecognizedUserIds()
    {
        return Stream.concat(Stream.of(this.selfUserId), this.decryptors.keySet().stream())
            .map(Long::toUnsignedString)
            .toArray(String[]::new);
    }


    private void handleDaveProtocolInit(short protocolVersion)
    {
        if (protocolVersion > DaveLibrary.DAVE_DISABLED_PROTOCOL_VERSION)
        {
            this.handlePrepareEpoch(DaveLibrary.DAVE_MLS_NEW_GROUP_EXPECTED_EPOCH, protocolVersion);
        }
        else
        {
            this.prepareProtocolTransition(DaveLibrary.DAVE_INIT_TRANSITION_ID, protocolVersion);
            this.executeProtocolTransition(DaveLibrary.DAVE_INIT_TRANSITION_ID);
        }
    }

    private void handlePrepareEpoch(long epoch, short protocolVersion)
    {
        if (epoch != DaveLibrary.DAVE_MLS_NEW_GROUP_EXPECTED_EPOCH)
            return;

        this.session.init(protocolVersion, this.channelId, Long.toUnsignedString(this.selfUserId));
        this.session.getMarshalledKeyPackage(this.callbacks::sendMLSKeyPackage);
    }

    private void prepareProtocolTransition(int transitionId, short protocolVersion)
    {
        this.decryptors.forEach((userId, decryptor) ->
        {
            if (this.selfUserId != userId.longValue())
            {
                decryptor._prepareTransition(this.session, protocolVersion, userId);
            }
        });
        if (transitionId == DaveLibrary.DAVE_INIT_TRANSITION_ID)
        {
            this.encryptor._processTransition(this.session, protocolVersion, this.selfUserId);
        }
        else
        {
            this.preparedTransitions.put(transitionId, protocolVersion);
            this.currentProtocolVersion = protocolVersion;
            this.callbacks.sendDaveProtocolReadyForTransition(transitionId);
        }
    }

    private void executeProtocolTransition(int transitionId)
    {
        Short protocolVersion = this.preparedTransitions.remove(transitionId);
        if (protocolVersion == null)
            return;
        if (protocolVersion == DaveLibrary.DAVE_DISABLED_PROTOCOL_VERSION)
            this.session.reset();
        this.encryptor._processTransition(this.session, protocolVersion, this.selfUserId);
    }

    private void sendInvalidCommitWelcome(int transitionId)
    {
        this.callbacks.sendMLSInvalidCommitWelcome(transitionId);
        this.session.getMarshalledKeyPackage(this.callbacks::sendMLSKeyPackage);
    }

}
