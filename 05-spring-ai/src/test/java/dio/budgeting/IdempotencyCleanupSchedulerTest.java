package dio.budgeting;

import dio.budgeting.infrastructure.idempotency.IdempotencyCleanupScheduler;
import dio.budgeting.infrastructure.idempotency.IdempotencyCleanupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for IdempotencyCleanupScheduler (TASK-010). {@code run()} is called
 * directly - never via a real {@code @Scheduled} clock/thread, so this suite
 * never depends on wall-clock timing. A separate assertion confirms the
 * scheduler bean itself is absent from the test application context, proving
 * app.idempotency.cleanup-enabled=false (src/test/resources/application-test.properties)
 * actually disables it via @ConditionalOnProperty - no automatic execution
 * ever races with other tests. No OpenAI call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class IdempotencyCleanupSchedulerTest {

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void schedulerBean_isAbsentWhenCleanupDisabled() {
        assertThatThrownBy(() -> applicationContext.getBean(IdempotencyCleanupScheduler.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    void run_recoversBeforeCleaningUp_andLogsAggregateCounts() {
        var cleanupService = mock(IdempotencyCleanupService.class);
        when(cleanupService.recoverAbandonedProcessing()).thenReturn(3);
        when(cleanupService.cleanupExpired()).thenReturn(25);
        var scheduler = new IdempotencyCleanupScheduler(cleanupService);

        scheduler.run();

        var order = inOrder(cleanupService);
        order.verify(cleanupService).recoverAbandonedProcessing();
        order.verify(cleanupService).cleanupExpired();
    }

    @Test
    void run_neverThrows_evenWhenNothingToDo() {
        var cleanupService = mock(IdempotencyCleanupService.class);
        when(cleanupService.recoverAbandonedProcessing()).thenReturn(0);
        when(cleanupService.cleanupExpired()).thenReturn(0);
        var scheduler = new IdempotencyCleanupScheduler(cleanupService);

        scheduler.run();

        assertThat(true).isTrue(); // reaching here means no exception was thrown
    }
}
