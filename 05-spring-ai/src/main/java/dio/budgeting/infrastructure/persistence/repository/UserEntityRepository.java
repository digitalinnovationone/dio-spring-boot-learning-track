package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserEntityRepository extends CrudRepository<UserEntity, UUID> {
    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);
}
