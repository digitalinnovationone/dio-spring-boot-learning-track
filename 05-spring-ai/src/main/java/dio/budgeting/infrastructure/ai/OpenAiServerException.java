package dio.budgeting.infrastructure.ai;

import org.springframework.ai.retry.TransientAiException;

/**
 * Thrown by {@link OpenAiHttpErrorHandlerConfig}'s {@code ResponseErrorHandler} for
 * OpenAI 5xx responses. Extends {@link TransientAiException} so it keeps matching
 * the retry policy's {@code includes(TransientAiException.class)} rule, while also
 * carrying the real HTTP status as a typed field instead of embedding it in the
 * exception message (which the app never parses).
 */
public class OpenAiServerException extends TransientAiException {

    private final int statusCode;

    public OpenAiServerException(int statusCode) {
        super("OpenAI request failed with HTTP " + statusCode);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
