package dio.budgeting.infrastructure.persistence;

import dio.budgeting.domain.AudioRecord;
import dio.budgeting.domain.AudioRecordRepository;
import dio.budgeting.domain.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JpaAudioRecordRepositoryTest {

    @Autowired
    private AudioRecordRepository audioRecordRepository;

    @Test
    void shouldSaveAndRetrieveAudioRecord() {
        var userId = new UserId();
        var record = new AudioRecord(
                userId,
                "command.wav",
                "internal-123.wav",
                "audio/wav",
                2048L,
                "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e",
                "./uploads/audio/internal-123.wav"
        );

        var saved = audioRecordRepository.save(record);

        assertThat(saved.getId()).isEqualTo(record.getId());
        assertThat(saved.getOriginalFileName()).isEqualTo("command.wav");
        assertThat(saved.getChecksumSha256()).isEqualTo(record.getChecksumSha256());

        var foundById = audioRecordRepository.findById(record.getId());
        assertThat(foundById).isPresent();
        assertThat(foundById.get().getUserId()).isEqualTo(userId);

        var userRecords = audioRecordRepository.findAllByUserId(userId);
        assertThat(userRecords).hasSize(1);
        assertThat(userRecords.get(0).getId()).isEqualTo(record.getId());
    }
}
