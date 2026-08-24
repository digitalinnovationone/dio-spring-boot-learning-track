package dio.budgeting.infrastructure.http;

import dio.budgeting.application.AuthenticateUserUseCase;
import dio.budgeting.application.RegisterUserUseCase;
import dio.budgeting.infrastructure.http.request.LoginRequest;
import dio.budgeting.infrastructure.http.request.RegisterRequest;
import dio.budgeting.infrastructure.http.response.AuthResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final AuthenticateUserUseCase authenticateUserUseCase;

    public AuthController(
            RegisterUserUseCase registerUserUseCase,
            AuthenticateUserUseCase authenticateUserUseCase) {
        this.registerUserUseCase = registerUserUseCase;
        this.authenticateUserUseCase = authenticateUserUseCase;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@RequestBody RegisterRequest request) {
        var user = registerUserUseCase.execute(request.toInput());
        return Map.of(
                "id", user.getId().uuid().toString(),
                "username", user.getUsername(),
                "role", user.getRole().name(),
                "message", "User registered successfully"
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        var tokenOutput = authenticateUserUseCase.execute(request.toInput());
        return ResponseEntity.ok(AuthResponse.from(tokenOutput));
    }
}
