package dio.budgeting.infrastructure.idempotency;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 fingerprint of the raw audio bytes, used only to detect reuse of an
 * {@code Idempotency-Key} with a different file - never to derive identity by
 * itself (two different recordings of the same command hash differently,
 * two unrelated files could theoretically collide). The digest is one-way:
 * the original bytes are never stored or recoverable from it.
 */
public final class AudioPayloadFingerprint {

    private AudioPayloadFingerprint() {
    }

    public static String of(byte[] audio) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(audio);
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }
}
