package dio.budgeting.application.input;

public record AuthenticateUserInput(String username, String password) {
    public AuthenticateUserInput {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be blank");
        }
    }
}
