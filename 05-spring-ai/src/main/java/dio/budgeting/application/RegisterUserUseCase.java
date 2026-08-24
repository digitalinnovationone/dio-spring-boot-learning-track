package dio.budgeting.application;

import dio.budgeting.application.input.RegisterUserInput;
import dio.budgeting.domain.User;
import dio.budgeting.domain.UserRepository;
import dio.budgeting.domain.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterUserUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User execute(RegisterUserInput input) {
        if (userRepository.existsByUsername(input.username())) {
            throw new IllegalArgumentException("Username already exists: " + input.username());
        }

        String encodedPassword = passwordEncoder.encode(input.password());
        User newUser = new User(input.username(), encodedPassword, UserRole.ROLE_USER);

        return userRepository.save(newUser);
    }
}
