package dio.budgeting.infrastructure.idempotency;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Wires idempotency expiration/cleanup (TASK-010): a single UTC {@link Clock}
 * (the only clock read anywhere in this package - see
 * {@code IdempotencyExpirationPolicy} and {@code AudioCommandOperationStore})
 * and Spring's {@code @Scheduled} infrastructure. Kept separate from {@code
 * BudgetingApplication} so scheduling support isn't silently enabled by a
 * change unrelated to idempotency. {@code IdempotencyCleanupScheduler} itself
 * stays disabled via {@code @ConditionalOnProperty} whenever {@code
 * app.idempotency.cleanup-enabled=false} (the test profile default), so
 * {@code @EnableScheduling} alone never causes non-deterministic test runs.
 */
@Configuration
@EnableConfigurationProperties(IdempotencyProperties.class)
@EnableScheduling
public class IdempotencyConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
