package dio.budgeting.infrastructure.http.error;

public record FieldValidationError(
        String field,
        String message
) {
}
