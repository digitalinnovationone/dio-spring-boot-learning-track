package dio.budgeting.infrastructure.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic recovery+cleanup for {@code audio_command_operation} (TASK-010).
 * Recovery always runs before cleanup in the same tick: a row just marked
 * FAILED by recovery has a fresh {@code updatedAt}, so it is never also
 * eligible for deletion in that same pass.
 *
 * <p>Disabled entirely via {@code app.idempotency.cleanup-enabled=false} (the
 * test profile default - see {@code src/test/resources/application-test.properties}),
 * so the test suite never runs this on a real clock; {@code
 * IdempotencyCleanupSchedulerTest} calls {@link #run()} directly instead.
 *
 * <p>Never calls OpenAI, never reprocesses audio, never touches transactions -
 * only status/timestamps on this one table, and only aggregate counts are
 * logged (never the key, fingerprint, or response text).
 */
@Component
@ConditionalOnProperty(prefix = "app.idempotency", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);

    private final IdempotencyCleanupService cleanupService;

    public IdempotencyCleanupScheduler(IdempotencyCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(fixedDelayString = "${app.idempotency.cleanup-interval}")
    public void run() {
        int recovered = cleanupService.recoverAbandonedProcessing();
        int removed = cleanupService.cleanupExpired();
        log.info("Idempotência: operações abandonadas recuperadas={}, operações expiradas removidas={}", recovered, removed);
    }
}
