package dio.budgeting;

import dio.budgeting.infrastructure.idempotency.AudioPayloadFingerprint;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AudioPayloadFingerprintTest {

    @Test
    void sameBytes_produceSameFingerprint() {
        byte[] audio = "conteudo-de-audio".getBytes(StandardCharsets.UTF_8);

        assertThat(AudioPayloadFingerprint.of(audio)).isEqualTo(AudioPayloadFingerprint.of(audio.clone()));
    }

    @Test
    void differentBytes_produceDifferentFingerprint() {
        byte[] first = "gastei 50 reais no mercado".getBytes(StandardCharsets.UTF_8);
        byte[] second = "gastei 51 reais no mercado".getBytes(StandardCharsets.UTF_8);

        assertThat(AudioPayloadFingerprint.of(first)).isNotEqualTo(AudioPayloadFingerprint.of(second));
    }

    @Test
    void fingerprint_isSha256HexEncoded_notMd5Length() {
        String fingerprint = AudioPayloadFingerprint.of(new byte[]{1, 2, 3});

        // SHA-256 -> 32 bytes -> 64 hex chars. MD5 would be 32 hex chars: this
        // length check is what rules out MD5 without hardcoding an algorithm name.
        assertThat(fingerprint).hasSize(64);
        assertThat(fingerprint).matches("[0-9a-f]{64}");
    }

    @Test
    void fingerprint_doesNotExposeOriginalBytesAsSubstring() {
        byte[] audio = "segredo-do-audio-em-texto-puro".getBytes(StandardCharsets.UTF_8);

        String fingerprint = AudioPayloadFingerprint.of(audio);

        assertThat(fingerprint).doesNotContain("segredo-do-audio-em-texto-puro");
    }

    @Test
    void emptyArray_stillProducesAValidFingerprint() {
        assertThat(AudioPayloadFingerprint.of(new byte[0])).hasSize(64);
    }
}
