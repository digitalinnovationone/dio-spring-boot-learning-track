package dio.budgeting.infrastructure.http.response;

import dio.budgeting.application.output.AuthTokenOutput;

public record AuthResponse(String token, String type, long expiresInMs) {
    public static AuthResponse from(AuthTokenOutput output) {
        return new AuthResponse(output.token(), output.type(), output.expiresInMs());
    }
}
