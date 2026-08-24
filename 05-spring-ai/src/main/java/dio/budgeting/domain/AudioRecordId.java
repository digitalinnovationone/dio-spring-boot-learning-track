package dio.budgeting.domain;

import java.util.UUID;

public record AudioRecordId(UUID uuid) {
    public AudioRecordId {
        if (uuid == null) {
            throw new IllegalArgumentException("AudioRecordId cannot be null");
        }
    }

    public AudioRecordId() {
        this(UUID.randomUUID());
    }

    public static AudioRecordId fromString(String id) {
        return new AudioRecordId(UUID.fromString(id));
    }
}
