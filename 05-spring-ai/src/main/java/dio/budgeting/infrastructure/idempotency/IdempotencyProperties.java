package dio.budgeting.infrastructure.idempotency;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Idempotency retention/cleanup policy (TASK-010), externalized so each
 * environment can tune retention without a code change. {@code
 * completedRetention}/{@code failedRetention}/{@code processingTimeout}
 * accept Spring Boot's simple {@link Duration} syntax (e.g. {@code "24h"},
 * {@code "15m"}). {@code cleanupInterval} must stay ISO-8601 ({@code "PT1H"})
 * because {@code IdempotencyCleanupScheduler} also reads it verbatim as a raw
 * {@code @Scheduled(fixedDelayString = ...)} placeholder, which only
 * understands ISO-8601 durations or plain milliseconds - not Boot's relaxed
 * unit suffixes.
 */
@ConfigurationProperties(prefix = "app.idempotency")
public class IdempotencyProperties {

    private Duration completedRetention = Duration.ofHours(24);
    private Duration failedRetention = Duration.ofHours(24);
    private Duration processingTimeout = Duration.ofMinutes(15);
    private Duration cleanupInterval = Duration.ofHours(1);
    private boolean cleanupEnabled = true;
    private int batchSize = 100;

    public Duration getCompletedRetention() {
        return completedRetention;
    }

    public void setCompletedRetention(Duration completedRetention) {
        this.completedRetention = completedRetention;
    }

    public Duration getFailedRetention() {
        return failedRetention;
    }

    public void setFailedRetention(Duration failedRetention) {
        this.failedRetention = failedRetention;
    }

    public Duration getProcessingTimeout() {
        return processingTimeout;
    }

    public void setProcessingTimeout(Duration processingTimeout) {
        this.processingTimeout = processingTimeout;
    }

    public Duration getCleanupInterval() {
        return cleanupInterval;
    }

    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }

    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    public void setCleanupEnabled(boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    @PostConstruct
    public void validate() {
        requirePositive(completedRetention, "app.idempotency.completed-retention");
        requirePositive(failedRetention, "app.idempotency.failed-retention");
        requirePositive(processingTimeout, "app.idempotency.processing-timeout");
        requirePositive(cleanupInterval, "app.idempotency.cleanup-interval");
        if (batchSize <= 0) {
            throw new IllegalStateException("app.idempotency.batch-size deve ser maior que zero.");
        }
    }

    private static void requirePositive(Duration duration, String propertyName) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException(propertyName + " deve ser uma duração positiva (maior que zero).");
        }
    }
}
