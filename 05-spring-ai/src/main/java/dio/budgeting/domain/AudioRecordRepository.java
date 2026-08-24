package dio.budgeting.domain;

import java.util.List;
import java.util.Optional;

public interface AudioRecordRepository {
    AudioRecord save(AudioRecord audioRecord);

    Optional<AudioRecord> findById(AudioRecordId id);

    List<AudioRecord> findAllByUserId(UserId userId);
}
