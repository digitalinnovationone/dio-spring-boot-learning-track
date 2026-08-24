package dio.budgeting.domain;

import java.util.Optional;

public interface UserRepository {
    User save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}
