package net.sybyline.scarlet.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.prefs.Preferences;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EncryptedPrefs
{

    private static final Logger LOG = LoggerFactory.getLogger("EncryptedPrefs");

    private static final String DIGEST_METHOD = "SHA-256",
                                CIPHER_METHOD = "AES/GCM/NoPadding",
                                KEYGEN_METHOD = "PBKDF2WithHmacSHA256";
    private static final int    KEY_SIZE = 256,
                                KEYGEN_ITERATIONS = 100_000,
                                IV_LENGTH = 12,
                                GCM_TAG_SIZE = 128;
    private static final SecureRandom rand;
    static
    {
        SecureRandom srand;
        try
        {
            srand = SecureRandom.getInstanceStrong();
        }
        catch (NoSuchAlgorithmException e)
        {
            srand = new SecureRandom();
        }
        rand = srand;
    }
    private static final ThreadLocal<Cipher> threadLocalCipher = ThreadLocal.withInitial(EncryptedPrefs::createCipher);
    private static Cipher createCipher()
    {
        try
        {
            return Cipher.getInstance(CIPHER_METHOD);
        }
        catch (GeneralSecurityException e)
        {
            throw new AssertionError(e);
        }
    }
    private static byte[] crypt(int opmode, SecretKey key, byte[] iv, byte[] text) throws GeneralSecurityException
    {
        Cipher cipher = threadLocalCipher.get();
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_SIZE, iv));
        return cipher.doFinal(text);
    }

    public EncryptedPrefs(Preferences prefs, String globalPassword)
    {
        this.prefs = prefs;
        this.localPassword = init(prefs, globalPassword);
        this.keyCache = new ConcurrentHashMap<>();
    }
    private static char[] init(Preferences prefs, String globalPassword)
    {
        String absolutePath = prefs.absolutePath(),
               hash = hash(absolutePath + ":" + globalPassword);
        byte[] localPasswordBytes = prefs.getByteArray(hash, null);
        SecretKey initKey = derive(globalPassword.toCharArray(), absolutePath);
        if (localPasswordBytes != null)
            return decrypt(initKey, localPasswordBytes).toCharArray();
        byte[] bytes = new byte[32];
        rand.nextBytes(bytes);
        String localPassword = new String(Base64.getUrlEncoder().encode(bytes), StandardCharsets.UTF_8);
        prefs.putByteArray(hash, encrypt(initKey, localPassword));
        return localPassword.toCharArray();
    }

    private final Preferences prefs;
    private final char[] localPassword;
    private final Map<String, SecretKey> keyCache;

    public void put(String key, String value)
    {
        if (value == null)
            this.remove(key);
        else
            this.prefs.putByteArray(hash(key), encrypt(this.getOrDerive(key), value));
    }

    public String get(String key)
    {
        return decrypt(this.getOrDerive(key), this.prefs.getByteArray(hash(key), null));
    }

    public void remove(String key)
    {
        this.prefs.remove(hash(key));
    }

    public boolean contains(String key)
    {
        return this.prefs.getByteArray(hash(key), null) != null;
    }

    private SecretKey getOrDerive(String salt)
    {
        return this.keyCache.computeIfAbsent(salt, $ -> derive(this.localPassword, $));
    }

    private static SecretKey derive(char[] password, String salt)
    {
        try
        {
            return new SecretKeySpec(SecretKeyFactory.getInstance(KEYGEN_METHOD).generateSecret(new PBEKeySpec(password, salt.getBytes(StandardCharsets.UTF_8), KEYGEN_ITERATIONS, KEY_SIZE)).getEncoded(), "AES");
        }
        catch (GeneralSecurityException e)
        {
            throw new IllegalStateException("Derrivation failed", e);
        }
    }

    private static byte[] encrypt(SecretKey key, String value)
    {
        try
        {
            byte[] iv = new byte[IV_LENGTH];
            rand.nextBytes(iv);
            byte[] ciphertext = crypt(Cipher.DECRYPT_MODE, key, iv, Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)).concat(" ").getBytes(StandardCharsets.UTF_8)),
                   combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encode(combined);
        }
        catch (Exception e)
        {
            LOG.warn("Exception encrypting", e);
            return null;
        }
    }

    private static String decrypt(SecretKey key, byte[] combined)
    {
        try
        {
            if (combined == null)
                return null;
            combined = Base64.getDecoder().decode(combined);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH),
                   ciphertext = Arrays.copyOfRange(combined, IV_LENGTH, combined.length),
                   plaintext = crypt(Cipher.DECRYPT_MODE, key, iv, ciphertext);
            String s =  new String(plaintext, StandardCharsets.UTF_8);
            return new String(Base64.getDecoder().decode(s.substring(0, s.indexOf(' '))), StandardCharsets.UTF_8);
        }
        catch (GeneralSecurityException e)
        {
            LOG.warn("Exception decrypting", e);
            return null;
        }
    }

    private static String hash(String key)
    {
        try
        {
            byte[] bytes = MessageDigest.getInstance(DIGEST_METHOD).digest(key.getBytes(StandardCharsets.UTF_8));
            char[] chars = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++)
            {
                byte b = bytes[i];
                chars[i * 2] = Character.forDigit((b >>> 4) & 0xF, 16);
                chars[i * 2 + 1] = Character.forDigit(b & 0xF, 16);
            }
            return new String(chars);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new AssertionError(e);
        }
    }

}
