package dio.budgeting.infrastructure.idempotency;

import java.util.regex.Pattern;

/**
 * Validates the opaque {@code Idempotency-Key} header: client-generated,
 * reused only for the same logical command. No format is imposed beyond a
 * safe, boring token shape (also matched by UUIDs) and a length cap - the
 * contract does not require a UUID.
 */
final class IdempotencyKeyValidator {

    static final int MAX_LENGTH = 128;

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._:-]+");

    private IdempotencyKeyValidator() {
    }

    static void validate(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyException(IdempotencyException.Reason.MISSING_KEY,
                    "O header Idempotency-Key é obrigatório.");
        }
        if (idempotencyKey.length() > MAX_LENGTH || !ALLOWED.matcher(idempotencyKey).matches()) {
            throw new IdempotencyException(IdempotencyException.Reason.INVALID_KEY,
                    "O header Idempotency-Key possui formato inválido.");
        }
    }
}
