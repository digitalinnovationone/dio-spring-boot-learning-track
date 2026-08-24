package dio.budgeting.application;

import dio.budgeting.application.input.AuthenticateUserInput;
import dio.budgeting.application.output.AuthTokenOutput;
import dio.budgeting.domain.UserRepository;
import dio.budgeting.infrastructure.security.AuthenticatedUser;
import dio.budgeting.infrastructure.security.jwt.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthenticateUserUseCase {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final long expirationMs;

    public AuthenticateUserUseCase(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            JwtService jwtService,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.expirationMs = expirationMs;
    }

    public AuthTokenOutput execute(AuthenticateUserInput input) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(input.username(), input.password())
        );

        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        String token = jwtService.generateToken(authenticatedUser, authenticatedUser.getId());

        return AuthTokenOutput.bearer(token, expirationMs);
    }
}
