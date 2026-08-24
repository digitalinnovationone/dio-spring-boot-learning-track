package dio.budgeting.infrastructure.http.request;

import dio.budgeting.application.input.RegisterUserInput;

public record RegisterRequest(String username, String password) {
    public RegisterUserInput toInput() {
        return new RegisterUserInput(username, password);
    }
}
