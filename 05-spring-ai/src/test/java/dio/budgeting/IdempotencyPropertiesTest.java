package dio.budgeting;

import dio.budgeting.infrastructure.idempotency.IdempotencyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for IdempotencyProperties (TASK-010): default values and the
 * @PostConstruct validation that rejects non-positive durations/batch size.
 * No Spring context needed for the direct-instantiation tests; the last test
 * proves the wiring actually fails application startup on an invalid value.
 */
class IdempotencyPropertiesTest {

    @Test
    void defaults_areValid() {
        var properties = new IdempotencyProperties();

        assertThat(properties.getCompletedRetention()).isEqualTo(Duration.ofHours(24));
        assertThat(properties.getFailedRetention()).isEqualTo(Duration.ofHours(24));
        assertThat(properties.getProcessingTimeout()).isEqualTo(Duration.ofMinutes(15));
        assertThat(properties.getCleanupInterval()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.isCleanupEnabled()).isTrue();
        assertThat(properties.getBatchSize()).isEqualTo(100);

        properties.validate();
    }

    @Test
    void zeroCompletedRetention_throwsOnValidate() {
        var properties = new IdempotencyProperties();
        properties.setCompletedRetention(Duration.ZERO);

        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void negativeFailedRetention_throwsOnValidate() {
        var properties = new IdempotencyProperties();
        properties.setFailedRetention(Duration.ofMinutes(-1));

        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void zeroProcessingTimeout_throwsOnValidate() {
        var properties = new IdempotencyProperties();
        properties.setProcessingTimeout(Duration.ZERO);

        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void negativeCleanupInterval_throwsOnValidate() {
        var properties = new IdempotencyProperties();
        properties.setCleanupInterval(Duration.ofMinutes(-5));

        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void zeroBatchSize_throwsOnValidate() {
        var properties = new IdempotencyProperties();
        properties.setBatchSize(0);

        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void negativeBatchSize_throwsOnValidate() {
        var properties = new IdempotencyProperties();
        properties.setBatchSize(-10);

        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cleanupDisabled_isConfigurable() {
        var properties = new IdempotencyProperties();
        properties.setCleanupEnabled(false);

        assertThat(properties.isCleanupEnabled()).isFalse();
        properties.validate();
    }

    @Test
    void invalidPropertyValue_failsApplicationContextStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues("app.idempotency.completed-retention=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void validPropertyValue_bindsAndStartsNormally() {
        new ApplicationContextRunner()
                .withUserConfiguration(TestConfig.class)
                .withPropertyValues(
                        "app.idempotency.completed-retention=2h",
                        "app.idempotency.batch-size=50")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(IdempotencyProperties.class);
                    assertThat(properties.getCompletedRetention()).isEqualTo(Duration.ofHours(2));
                    assertThat(properties.getBatchSize()).isEqualTo(50);
                });
    }

    @Configuration
    @EnableConfigurationProperties(IdempotencyProperties.class)
    static class TestConfig {
    }
}
