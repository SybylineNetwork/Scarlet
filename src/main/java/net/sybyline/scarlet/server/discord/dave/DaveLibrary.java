package net.sybyline.scarlet.server.discord.dave;

import java.nio.ByteBuffer;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.PointerType;
import com.sun.jna.StringArray;
import com.sun.jna.Structure;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

public interface DaveLibrary extends Library
{

    abstract class OpaqueHandle extends PointerType
    {
        public OpaqueHandle() { super(); } public OpaqueHandle(Pointer p) { super(p); }
        public abstract class ByReference<OH extends OpaqueHandle> extends com.sun.jna.ptr.ByReference
        {
            public ByReference() { this(null); }
            public ByReference(OH handle) { super(Native.POINTER_SIZE); this.setHandle(handle); }
            public OH getHandle()
            { Pointer p = this.getPointer().getPointer(0L); return Pointer.nativeValue(p) == 0L ? null : this.create(p); }
            public void setHandle(OH handle)
            { this.getPointer().setPointer(0L, handle == null ? null : handle.getPointer()); }
            protected abstract OH create(Pointer p);
        }
    }

    DaveLibrary INSTANCE = Native.load("libdave", DaveLibrary.class);

    class DAVESessionHandle extends OpaqueHandle
    {   public DAVESessionHandle() { super(); } public DAVESessionHandle(Pointer p) { super(p); }
        public class ByReference extends OpaqueHandle.ByReference<DAVESessionHandle>
        {   public ByReference() { super(); } public ByReference(DAVESessionHandle handle) { super(handle); }
            @Override protected DAVESessionHandle create(Pointer p) { return new DAVESessionHandle(p); }
        }
    }
    class DAVECommitResultHandle extends OpaqueHandle
    {   public DAVECommitResultHandle() { super(); } public DAVECommitResultHandle(Pointer p) { super(p); }
        public class ByReference extends OpaqueHandle.ByReference<DAVECommitResultHandle>
        {   public ByReference() { super(); } public ByReference(DAVECommitResultHandle handle) { super(handle); }
            @Override protected DAVECommitResultHandle create(Pointer p) { return new DAVECommitResultHandle(p); }
        }
    }
    class DAVEWelcomeResultHandle extends OpaqueHandle
    {   public DAVEWelcomeResultHandle() { super(); } public DAVEWelcomeResultHandle(Pointer p) { super(p); }
        public class ByReference extends OpaqueHandle.ByReference<DAVEWelcomeResultHandle>
        {   public ByReference() { super(); } public ByReference(DAVEWelcomeResultHandle handle) { super(handle); }
            @Override protected DAVEWelcomeResultHandle create(Pointer p) { return new DAVEWelcomeResultHandle(p); }
        }
    }
    class DAVEKeyRatchetHandle extends OpaqueHandle
    {   public DAVEKeyRatchetHandle() { super(); } public DAVEKeyRatchetHandle(Pointer p) { super(p); }
        public class ByReference extends OpaqueHandle.ByReference<DAVEKeyRatchetHandle>
        {   public ByReference() { super(); } public ByReference(DAVEKeyRatchetHandle handle) { super(handle); }
            @Override protected DAVEKeyRatchetHandle create(Pointer p) { return new DAVEKeyRatchetHandle(p); }
        }
    }
    class DAVEEncryptorHandle extends OpaqueHandle
    {   public DAVEEncryptorHandle() { super(); } public DAVEEncryptorHandle(Pointer p) { super(p); }
        public class ByReference extends OpaqueHandle.ByReference<DAVEEncryptorHandle>
        {   public ByReference() { super(); } public ByReference(DAVEEncryptorHandle handle) { super(handle); }
            @Override protected DAVEEncryptorHandle create(Pointer p) { return new DAVEEncryptorHandle(p); }
        }
    }
    class DAVEDecryptorHandle extends OpaqueHandle
    {   public DAVEDecryptorHandle() { super(); } public DAVEDecryptorHandle(Pointer p) { super(p); }
        public class ByReference extends OpaqueHandle.ByReference<DAVEDecryptorHandle>
        {   public ByReference() { super(); } public ByReference(DAVEDecryptorHandle handle) { super(handle); }
            @Override protected DAVEDecryptorHandle create(Pointer p) { return new DAVEDecryptorHandle(p); }
        }
    }

    int DAVE_CODEC_UNKNOWN = 0, /**< Unknown or unspecified codec */
        DAVE_CODEC_OPUS = 1,    /**< Opus audio codec */
        DAVE_CODEC_VP8 = 2,     /**< VP8 video codec */
        DAVE_CODEC_VP9 = 3,     /**< VP9 video codec */
        DAVE_CODEC_H264 = 4,    /**< H.264/AVC video codec */
        DAVE_CODEC_H265 = 5,    /**< H.265/HEVC video codec */
        DAVE_CODEC_AV1 = 6;     /**< AV1 video codec */

    int DAVE_MEDIA_TYPE_AUDIO = 0, /**< Audio stream */
        DAVE_MEDIA_TYPE_VIDEO = 1; /**< Video stream */

    int DAVE_ENCRYPTOR_RESULT_CODE_SUCCESS = 0,            /**< Encryption succeeded */
        DAVE_ENCRYPTOR_RESULT_CODE_ENCRYPTION_FAILURE = 1, /**< Encryption failed */
        DAVE_ENCRYPTOR_RESULT_CODE_MISSING_KEY_RATCHET = 2,/**< No key ratchet available */
        DAVE_ENCRYPTOR_RESULT_CODE_MISSING_CRYPTOR = 3,    /**< Missing cryptographic context */
        DAVE_ENCRYPTOR_RESULT_CODE_TOO_MANY_ATTEMPTS = 4;  /**< Too many attempts to encrypt the frame failed */

    int DAVE_DECRYPTOR_RESULT_CODE_SUCCESS = 0,            /**< Decryption succeeded */
        DAVE_DECRYPTOR_RESULT_CODE_DECRYPTION_FAILURE = 1, /**< Decryption failed */
        DAVE_DECRYPTOR_RESULT_CODE_MISSING_KEY_RATCHET = 2,/**< No key ratchet available */
        DAVE_DECRYPTOR_RESULT_CODE_INVALID_NONCE = 3,      /**< Invalid nonce in encrypted frame */
        DAVE_DECRYPTOR_RESULT_CODE_MISSING_CRYPTOR = 4;    /**< Missing cryptographic context */

    int DAVE_LOGGING_SEVERITY_VERBOSE = 0, /**< Verbose debug information */
        DAVE_LOGGING_SEVERITY_INFO = 1,    /**< Informational messages */
        DAVE_LOGGING_SEVERITY_WARNING = 2, /**< Warning messages */
        DAVE_LOGGING_SEVERITY_ERROR = 3,   /**< Error messages */
        DAVE_LOGGING_SEVERITY_NONE = 4;    /**< Messages to be ignored */

    short DAVE_DISABLED_PROTOCOL_VERSION = 0;
    long DAVE_MLS_NEW_GROUP_EXPECTED_EPOCH = 1L;
    int DAVE_INIT_TRANSITION_ID = 0;

    @FunctionalInterface interface DAVEMLSFailureCallback extends Callback
    { void callback(String source, String reason, Pointer userData); }

    @FunctionalInterface interface DAVEPairwiseFingerprintCallback extends Callback
    { void callback(Pointer fingerprint, long length, Pointer userData); }

    @FunctionalInterface interface DAVEEncryptorProtocolVersionChangedCallback extends Callback
    { void callback(Pointer userData); }

    @FunctionalInterface interface DAVELogSinkCallback extends Callback
    { void callback(int severity, String file, int line, String message); }

    @Structure.FieldOrder({"passthroughCount","encryptSuccessCount","encryptFailureCount","encryptDuration","encryptAttempts","encryptMaxAttempts","encryptMissingKeyCount"})
    class DAVEEncryptorStats extends Structure
    {
        public DAVEEncryptorStats() {} public DAVEEncryptorStats(Pointer p) { super(p); this.read(); }
        public static class ByReference extends DAVEEncryptorStats implements Structure.ByReference
        { public ByReference() {} public ByReference(Pointer p) { super(p); } }
        public static class ByValue extends DAVEEncryptorStats implements Structure.ByValue
        { public ByValue() {} public ByValue(Pointer p) { super(p); } }
        public long passthroughCount;
        public long encryptSuccessCount;
        public long encryptFailureCount;
        public long encryptDuration;
        public long encryptAttempts;
        public long encryptMaxAttempts;
        public long encryptMissingKeyCount;
    }

    @Structure.FieldOrder({"passthroughCount","decryptSuccessCount","decryptFailureCount","decryptDuration","decryptAttempts","decryptMissingKeyCount","decryptInvalidNonceCount"})
    class DAVEDecryptorStats extends Structure
    {
        public DAVEDecryptorStats() {} public DAVEDecryptorStats(Pointer p) { super(p); this.read(); }
        public static class ByReference extends DAVEDecryptorStats implements Structure.ByReference
        { public ByReference() {} public ByReference(Pointer p) { super(p); } }
        public static class ByValue extends DAVEDecryptorStats implements Structure.ByValue
        { public ByValue() {} public ByValue(Pointer p) { super(p); } }
        public long passthroughCount;
        public long decryptSuccessCount;
        public long decryptFailureCount;
        public long decryptDuration;
        public long decryptAttempts;
        public long decryptMissingKeyCount;
        public long decryptInvalidNonceCount;
    }

    short daveMaxSupportedProtocolVersion();

    void daveFree(Pointer ptr);

    DAVESessionHandle daveSessionCreate(Pointer context, String authSessionId, DAVEMLSFailureCallback callback, Pointer userData);

    void daveSessionDestroy(DAVESessionHandle session);

    void daveSessionInit(DAVESessionHandle session, short version, long groupId, String selfUserId);

    void daveSessionReset(DAVESessionHandle session);

    void daveSessionSetProtocolVersion(DAVESessionHandle session, short version);

    short daveSessionGetProtocolVersion(DAVESessionHandle session);

    void daveSessionGetLastEpochAuthenticator(DAVESessionHandle session, PointerByReference authenticator, LongByReference length);

    void daveSessionSetExternalSender(DAVESessionHandle session, Pointer externalSender, long length);
    void daveSessionSetExternalSender(DAVESessionHandle session, ByteBuffer externalSender, long length);

    void daveSessionProcessProposals(DAVESessionHandle session, Pointer proposals, long length, StringArray recognizedUserIds, long recognizedUserIdsLength, PointerByReference commitWelcomeBytes, LongByReference commitWelcomeBytesLength);
    void daveSessionProcessProposals(DAVESessionHandle session, ByteBuffer proposals, long length, StringArray recognizedUserIds, long recognizedUserIdsLength, PointerByReference commitWelcomeBytes, LongByReference commitWelcomeBytesLength);

    DAVECommitResultHandle daveSessionProcessCommit(DAVESessionHandle session, Pointer commit, long length);
    DAVECommitResultHandle daveSessionProcessCommit(DAVESessionHandle session, ByteBuffer commit, long length);

    DAVEWelcomeResultHandle daveSessionProcessWelcome(DAVESessionHandle session, Pointer welcome, long length, StringArray recognizedUserIds, long recognizedUserIdsLength);
    DAVEWelcomeResultHandle daveSessionProcessWelcome(DAVESessionHandle session, ByteBuffer welcome, long length, StringArray recognizedUserIds, long recognizedUserIdsLength);

    void daveSessionGetMarshalledKeyPackage(DAVESessionHandle session, PointerByReference keyPackage, LongByReference length);

    DAVEKeyRatchetHandle daveSessionGetKeyRatchet(DAVESessionHandle session, String userId);

    void daveSessionGetPairwiseFingerprint(DAVESessionHandle session, short version, String userId, DAVEPairwiseFingerprintCallback callback, Pointer userData);

    void daveKeyRatchetDestroy(DAVEKeyRatchetHandle keyRatchet);

    boolean daveCommitResultIsFailed(DAVECommitResultHandle commitResultHandle);

    boolean daveCommitResultIsIgnored(DAVECommitResultHandle commitResultHandle);

    void daveCommitResultGetRosterMemberIds(DAVECommitResultHandle commitResultHandle, PointerByReference rosterIds, LongByReference rosterIdsLength);

    void daveCommitResultGetRosterMemberSignature(DAVECommitResultHandle commitResultHandle, long rosterId, PointerByReference signature, LongByReference signatureLength);

    void daveCommitResultDestroy(DAVECommitResultHandle commitResultHandle);

    void daveWelcomeResultGetRosterMemberIds(DAVEWelcomeResultHandle welcomeResultHandle, PointerByReference rosterIds, LongByReference rosterIdsLength);

    void daveWelcomeResultGetRosterMemberSignature(DAVEWelcomeResultHandle welcomeResultHandle, long rosterId, PointerByReference signature, LongByReference signatureLength);

    void daveWelcomeResultDestroy(DAVEWelcomeResultHandle welcomeResultHandle);

    DAVEEncryptorHandle daveEncryptorCreate();

    void daveEncryptorDestroy(DAVEEncryptorHandle encryptor);

    void daveEncryptorSetKeyRatchet(DAVEEncryptorHandle encryptor, DAVEKeyRatchetHandle keyRatchet);

    void daveEncryptorSetPassthroughMode(DAVEEncryptorHandle encryptor, boolean passthroughMode);

    void daveEncryptorAssignSsrcToCodec(DAVEEncryptorHandle encryptor, int ssrc, int codecType);

    short daveEncryptorGetProtocolVersion(DAVEEncryptorHandle encryptor);

    long daveEncryptorGetMaxCiphertextByteSize(DAVEEncryptorHandle encryptor, int mediaType, long frameSize);

    boolean daveEncryptorHasKeyRatchet(DAVEEncryptorHandle encryptor);

    boolean daveEncryptorIsPassthroughMode(DAVEEncryptorHandle encryptor);

    int daveEncryptorEncrypt(DAVEEncryptorHandle encryptor, int mediaType, int ssrc, Pointer frame, long frameLength, Pointer encryptedFrame, long encryptedFrameCapacity, LongByReference bytesWritten);
    int daveEncryptorEncrypt(DAVEEncryptorHandle encryptor, int mediaType, int ssrc, ByteBuffer frame, long frameLength, ByteBuffer encryptedFrame, long encryptedFrameCapacity, LongByReference bytesWritten);

    void daveEncryptorSetProtocolVersionChangedCallback(DAVEEncryptorHandle encryptor, DAVEEncryptorProtocolVersionChangedCallback callback);

    void daveEncryptorGetStats(DAVEEncryptorHandle encryptor, int mediaType, DAVEEncryptorStats.ByReference stats);

    DAVEDecryptorHandle daveDecryptorCreate();

    void daveDecryptorDestroy(DAVEDecryptorHandle decryptor);

    void daveDecryptorTransitionToKeyRatchet(DAVEDecryptorHandle decryptor, DAVEKeyRatchetHandle keyRatchet);

    void daveDecryptorTransitionToPassthroughMode(DAVEDecryptorHandle decryptor, boolean passthroughMode);

    int daveDecryptorDecrypt(DAVEDecryptorHandle decryptor, int mediaType, Pointer encryptedFrame, long encryptedFrameLength, Pointer frame, long frameCapacity, LongByReference bytesWritten);
    int daveDecryptorDecrypt(DAVEDecryptorHandle decryptor, int mediaType, ByteBuffer encryptedFrame, long encryptedFrameLength, ByteBuffer frame, long frameCapacity, LongByReference bytesWritten);

    long daveDecryptorGetMaxPlaintextByteSize(DAVEDecryptorHandle decryptor, int mediaType, long encryptedFrameSize);

    void daveDecryptorGetStats(DAVEDecryptorHandle decryptor, int mediaType, DAVEDecryptorStats.ByReference stats);

    void daveSetLogSinkCallback(DAVELogSinkCallback callback);

}
