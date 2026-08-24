package dio.budgeting.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class User {
    private UserId id;
    private String username;
    private String password;
    private UserRole role;
    private LocalDateTime createdAt;

    public User(String username, String password, UserRole role) {
        this(new UserId(), username, password, role, LocalDateTime.now());
    }

    public User(String username, String password) {
        this(username, password, UserRole.ROLE_USER);
    }
}
