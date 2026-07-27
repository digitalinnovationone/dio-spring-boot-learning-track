package dio.budgeting.infrastructure.http.error;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Documentation-only mirror of the {@link org.springframework.http.ProblemDetail}
 * shape actually returned by {@code GlobalExceptionHandler}, including the
 * additional {@code timestamp} and {@code errors} properties that springdoc
 * cannot infer from {@code ProblemDetail} alone. Never instantiated or
 * serialized at runtime.
 */
@Schema(description = "Resposta de erro padronizada (RFC 9457 Problem Details)")
public record ApiErrorResponse(
        @Schema(example = "about:blank") String type,
        @Schema(example = "Dados inválidos") String title,
        @Schema(example = "400") int status,
        @Schema(example = "Um ou mais campos possuem valores inválidos.") String detail,
        @Schema(example = "/transactions") String instance,
        @Schema(example = "2026-07-24T18:00:00-03:00") OffsetDateTime timestamp,
        List<FieldValidationError> errors
) {
}
