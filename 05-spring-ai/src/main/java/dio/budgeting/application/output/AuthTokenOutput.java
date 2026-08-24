package dio.budgeting.application.output;

public record AuthTokenOutput(String token, String type, long expiresInMs) {
    public static AuthTokenOutput bearer(String token, long expiresInMs) {
        return new AuthTokenOutput(token, "Bearer", expiresInMs);
    }
}
