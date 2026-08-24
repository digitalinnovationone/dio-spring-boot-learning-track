package dio.budgeting.infrastructure.persistence.entity;

import dio.budgeting.domain.User;
import dio.budgeting.domain.UserId;
import dio.budgeting.domain.UserRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserEntity {
    @Id
    private UUID id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static UserEntity from(User user) {
        return new UserEntity(
                user.getId().uuid(),
                user.getUsername(),
                user.getPassword(),
                user.getRole(),
                user.getCreatedAt() != null ? user.getCreatedAt() : LocalDateTime.now()
        );
    }

    public User toDomain() {
        return new User(
                new UserId(this.id),
                this.username,
                this.password,
                this.role,
                this.createdAt
        );
    }
}
