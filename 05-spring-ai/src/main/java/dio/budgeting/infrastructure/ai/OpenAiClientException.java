package dio.budgeting.infrastructure.ai;

import org.springframework.ai.retry.NonTransientAiException;

/**
 * Thrown by {@link OpenAiHttpErrorHandlerConfig}'s {@code ResponseErrorHandler} for
 * OpenAI 4xx responses (e.g. 401 invalid credential, 429 rate limit). Extends
 * {@link NonTransientAiException} so it is never retried by the shared retry
 * policy, while carrying the real HTTP status as a typed field.
 */
public class OpenAiClientException extends NonTransientAiException {

    private final int statusCode;

    public OpenAiClientException(int statusCode) {
        super("OpenAI request failed with HTTP " + statusCode);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
