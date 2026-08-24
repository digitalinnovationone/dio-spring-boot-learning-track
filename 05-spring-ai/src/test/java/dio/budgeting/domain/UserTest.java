package dio.budgeting.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void shouldCreateUserWithDefaultValues() {
        var user = new User("giovanni", "hashed_password");

        assertThat(user.getId()).isNotNull();
        assertThat(user.getId().uuid()).isNotNull();
        assertThat(user.getUsername()).isEqualTo("giovanni");
        assertThat(user.getPassword()).isEqualTo("hashed_password");
        assertThat(user.getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldCreateUserWithAdminRole() {
        var user = new User("admin", "admin_hash", UserRole.ROLE_ADMIN);

        assertThat(user.getRole()).isEqualTo(UserRole.ROLE_ADMIN);
    }

    @Test
    void shouldThrowExceptionWhenUserIdIsNull() {
        assertThatThrownBy(() -> new UserId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("UserId cannot be null");
    }
}
