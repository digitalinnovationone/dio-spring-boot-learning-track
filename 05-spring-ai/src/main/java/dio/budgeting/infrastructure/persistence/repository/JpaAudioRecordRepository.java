package dio.budgeting.infrastructure.persistence.repository;

import dio.budgeting.domain.AudioRecord;
import dio.budgeting.domain.AudioRecordId;
import dio.budgeting.domain.AudioRecordRepository;
import dio.budgeting.domain.UserId;
import dio.budgeting.infrastructure.persistence.entity.AudioRecordEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaAudioRecordRepository implements AudioRecordRepository {

    private final AudioRecordEntityRepository entityRepository;

    public JpaAudioRecordRepository(AudioRecordEntityRepository entityRepository) {
        this.entityRepository = entityRepository;
    }

    @Override
    public AudioRecord save(AudioRecord audioRecord) {
        var entity = AudioRecordEntity.from(audioRecord);
        return entityRepository.save(entity).toDomain();
    }

    @Override
    public Optional<AudioRecord> findById(AudioRecordId id) {
        return entityRepository.findById(id.uuid()).map(AudioRecordEntity::toDomain);
    }

    @Override
    public List<AudioRecord> findAllByUserId(UserId userId) {
        return entityRepository.findAllByUserId(userId.uuid())
                .stream()
                .map(AudioRecordEntity::toDomain)
                .toList();
    }
}
