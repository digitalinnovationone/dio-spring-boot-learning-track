package dio.budgeting.infrastructure.storage;

import dio.budgeting.domain.AudioRecordId;

public final class AudioRequestContextHolder {

    private static final ThreadLocal<AudioRecordId> CURRENT_AUDIO_RECORD_ID = new ThreadLocal<>();

    private AudioRequestContextHolder() {
    }

    public static void set(AudioRecordId id) {
        CURRENT_AUDIO_RECORD_ID.set(id);
    }

    public static AudioRecordId get() {
        return CURRENT_AUDIO_RECORD_ID.get();
    }

    public static void clear() {
        CURRENT_AUDIO_RECORD_ID.remove();
    }
}
