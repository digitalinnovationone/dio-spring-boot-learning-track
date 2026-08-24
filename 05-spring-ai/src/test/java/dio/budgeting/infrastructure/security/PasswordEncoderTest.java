package dio.budgeting.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void shouldEncodePasswordAndVerifySuccessfully() {
        String rawPassword = "my_secret_password_123";
        String encodedPassword = encoder.encode(rawPassword);

        assertThat(encodedPassword).isNotEqualTo(rawPassword);
        assertThat(encoder.matches(rawPassword, encodedPassword)).isTrue();
        assertThat(encoder.matches("wrong_password", encodedPassword)).isFalse();
    }
}
