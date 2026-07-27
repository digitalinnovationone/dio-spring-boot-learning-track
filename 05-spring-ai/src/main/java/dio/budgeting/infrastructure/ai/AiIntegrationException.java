package dio.budgeting.infrastructure.ai;

public class AiIntegrationException extends RuntimeException {

    public enum Stage {
        TRANSCRIPTION,
        CHAT,
        SPEECH
    }

    public enum Reason {
        TRANSCRIPTION_EMPTY,
        TIMEOUT,
        RATE_LIMITED,
        PROVIDER_UNAVAILABLE,
        INVALID_PROVIDER_RESPONSE,
        CONFIGURATION_ERROR,
        GENERIC_FAILURE
    }

    private final Stage stage;
    private final Reason reason;

    public AiIntegrationException(Stage stage, Reason reason, String message) {
        this(stage, reason, message, null);
    }

    public AiIntegrationException(Stage stage, Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
        this.reason = reason;
    }

    public Stage getStage() {
        return stage;
    }

    public Reason getReason() {
        return reason;
    }
}
