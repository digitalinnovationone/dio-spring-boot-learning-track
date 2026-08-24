package dio.budgeting.application.input;

public record RegisterUserInput(String username, String password) {
    public RegisterUserInput {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
    }
}
