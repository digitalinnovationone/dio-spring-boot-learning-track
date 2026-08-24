package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.AuthenticateUserInput;

public record LoginRequest(String username, String password) {
    public AuthenticateUserInput toInput() {
        return new AuthenticateUserInput(username, password);
    }
}
