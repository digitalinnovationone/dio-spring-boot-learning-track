package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.User;
import dio.budgeting.domain.UserId;
import dio.budgeting.domain.UserRepository;
import dio.budgeting.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaUserRepository implements UserRepository {
    private final UserEntityRepository userEntityRepository;

    public JpaUserRepository(UserEntityRepository userEntityRepository) {
        this.userEntityRepository = userEntityRepository;
    }

    @Override
    public User save(User user) {
        var entity = UserEntity.from(user);
        return userEntityRepository.save(entity).toDomain();
    }

    @Override
    public Optional<User> findById(UserId id) {
        return userEntityRepository.findById(id.uuid()).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userEntityRepository.findByUsername(username).map(UserEntity::toDomain);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userEntityRepository.existsByUsername(username);
    }
}
