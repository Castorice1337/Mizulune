package shit.zen.protocol.heypixel;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;

/** Wire-compatible default ID1/S2C payload transform recovered from the original client. */
public final class PbeMd5DesId1Crypto implements Id1PacketBuilder.Id1CryptoTransform {
    static final String ALGORITHM = "PBEWithMD5AndDES";
    static final int SALT_LENGTH = 8;
    static final int ITERATION_COUNT = 1000;

    private final char[] password;
    private final Supplier<byte[]> saltSource;

    public PbeMd5DesId1Crypto(UUID localUuid) {
        this(localUuid, secureSaltSource());
    }

    PbeMd5DesId1Crypto(UUID localUuid, Supplier<byte[]> saltSource) {
        this.password = Objects.requireNonNull(localUuid, "localUuid").toString().toCharArray();
        this.saltSource = Objects.requireNonNull(saltSource, "saltSource");
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public byte[] transform(byte[] preCrypto) {
        return encrypt(preCrypto);
    }

    public byte[] encrypt(byte[] plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        byte[] salt = Objects.requireNonNull(saltSource.get(), "salt").clone();
        if (salt.length != SALT_LENGTH) {
            throw new IllegalArgumentException("ID1 PBE salt must contain exactly 8 bytes");
        }
        byte[] ciphertext = crypt(Cipher.ENCRYPT_MODE, plaintext, salt);
        byte[] result = Arrays.copyOf(salt, salt.length + ciphertext.length);
        System.arraycopy(ciphertext, 0, result, salt.length, ciphertext.length);
        return result;
    }

    public byte[] decrypt(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        if (payload.length <= SALT_LENGTH) {
            throw new IllegalArgumentException("encrypted ID1 payload is shorter than salt + ciphertext");
        }
        byte[] salt = Arrays.copyOf(payload, SALT_LENGTH);
        byte[] ciphertext = Arrays.copyOfRange(payload, SALT_LENGTH, payload.length);
        return crypt(Cipher.DECRYPT_MODE, ciphertext, salt);
    }

    private byte[] crypt(int mode, byte[] input, byte[] salt) {
        PBEKeySpec keySpec = new PBEKeySpec(password);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            SecretKey key = factory.generateSecret(keySpec);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(mode, key, new PBEParameterSpec(salt, ITERATION_COUNT));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("ID1 PBE transform failed", error);
        } finally {
            keySpec.clearPassword();
        }
    }

    private static Supplier<byte[]> secureSaltSource() {
        SecureRandom random = new SecureRandom();
        return () -> {
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);
            return salt;
        };
    }
}
