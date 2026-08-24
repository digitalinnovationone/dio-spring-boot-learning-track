package dio.budgeting.infrastructure.security.jwt;

import dio.budgeting.domain.UserId;
import dio.budgeting.domain.UserRole;
import dio.budgeting.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long expirationMs = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secretKey, expirationMs);
    }

    @Test
    void shouldGenerateAndValidateTokenSuccessfully() {
        UserId userId = new UserId();
        AuthenticatedUser user = new AuthenticatedUser(userId, "john_doe", "password", UserRole.ROLE_USER);

        String token = jwtService.generateToken(user, userId);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("john_doe");
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
        assertThat(jwtService.isTokenExpired(token)).isFalse();

        var claims = jwtService.extractAllClaims(token);
        assertThat(claims.get("userId")).isEqualTo(userId.uuid().toString());
        assertThat(claims.get("role")).isEqualTo("ROLE_USER");
    }

    @Test
    void shouldReturnInvalidWhenTokenIsExpired() {
        UserId userId = new UserId();
        AuthenticatedUser user = new AuthenticatedUser(userId, "john_doe", "password", UserRole.ROLE_USER);

        // Gera token já expirado (-1000ms)
        String expiredToken = jwtService.generateToken(Map.of("userId", userId.uuid().toString()), "john_doe", -1000L);

        assertThat(jwtService.isTokenValid(expiredToken, user)).isFalse();
    }

    @Test
    void shouldReturnInvalidWhenUsernameDoesNotMatch() {
        UserId userId = new UserId();
        AuthenticatedUser user1 = new AuthenticatedUser(userId, "john_doe", "password", UserRole.ROLE_USER);
        AuthenticatedUser user2 = new AuthenticatedUser(userId, "jane_doe", "password", UserRole.ROLE_USER);

        String token = jwtService.generateToken(user1, userId);

        assertThat(jwtService.isTokenValid(token, user2)).isFalse();
    }
}
