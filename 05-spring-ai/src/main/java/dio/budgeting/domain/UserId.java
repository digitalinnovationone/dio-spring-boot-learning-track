package dio.budgeting.domain;

import java.util.UUID;

public record UserId(UUID uuid) {
    public UserId {
        if (uuid == null) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
    }

    public UserId() {
        this(UUID.randomUUID());
    }

    public static UserId fromString(String id) {
        return new UserId(UUID.fromString(id));
    }
}
