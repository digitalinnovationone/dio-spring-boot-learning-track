package dio.budgeting.infrastructure.persistence;

import dio.budgeting.domain.User;
import dio.budgeting.domain.UserRepository;
import dio.budgeting.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JpaUserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndFindUserByUsernameAndId() {
        var user = new User("alice", "$2a$10$hashedpasswordhere", UserRole.ROLE_USER);

        var savedUser = userRepository.save(user);

        assertThat(savedUser.getId()).isEqualTo(user.getId());
        assertThat(savedUser.getUsername()).isEqualTo("alice");

        var foundByUsername = userRepository.findByUsername("alice");
        assertThat(foundByUsername).isPresent();
        assertThat(foundByUsername.get().getUsername()).isEqualTo("alice");
        assertThat(foundByUsername.get().getRole()).isEqualTo(UserRole.ROLE_USER);

        var foundById = userRepository.findById(user.getId());
        assertThat(foundById).isPresent();
        assertThat(foundById.get().getUsername()).isEqualTo("alice");

        assertThat(userRepository.existsByUsername("alice")).isTrue();
        assertThat(userRepository.existsByUsername("non_existent")).isFalse();
    }
}
