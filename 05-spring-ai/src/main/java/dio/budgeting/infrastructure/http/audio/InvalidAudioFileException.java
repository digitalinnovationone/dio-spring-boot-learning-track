package dio.budgeting.infrastructure.http.audio;

public class InvalidAudioFileException extends RuntimeException {

    public enum Reason {
        EMPTY,
        TOO_LARGE,
        MISSING_CONTENT_TYPE,
        UNSUPPORTED_TYPE
    }

    private final Reason reason;

    public InvalidAudioFileException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
