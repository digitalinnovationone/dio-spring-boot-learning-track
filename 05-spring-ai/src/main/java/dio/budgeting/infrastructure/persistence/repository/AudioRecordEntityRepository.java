package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.infrastructure.persistence.entity.AudioRecordEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface AudioRecordEntityRepository extends CrudRepository<AudioRecordEntity, UUID> {
    List<AudioRecordEntity> findAllByUserId(UUID userId);
}
