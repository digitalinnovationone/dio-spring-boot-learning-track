package dio.budgeting.infrastructure.idempotency;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AudioCommandOperationRepository extends JpaRepository<AudioCommandOperationEntity, UUID> {

    Optional<AudioCommandOperationEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * Backs every temporal query used by recovery/cleanup (TASK-010): PROCESSING
     * abandoned past app.idempotency.processing-timeout, COMPLETED/FAILED past
     * their retention. Bounded by {@code pageable} so a large table is never
     * loaded in full - see AudioCommandOperationStore#findAbandonedProcessing /
     * #findExpired.
     */
    List<AudioCommandOperationEntity> findByStatusAndUpdatedAtBefore(
            AudioCommandStatus status, OffsetDateTime threshold, Pageable pageable);

    /**
     * Deletes only rows still matching status+threshold at delete time - the
     * WHERE clause re-checks both, closing the race where a row changed state
     * (e.g. a retried FAILED row becoming PROCESSING again) between an earlier
     * select and this delete: such a row is silently skipped, never deleted.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AudioCommandOperationEntity e "
            + "where e.status = :status and e.updatedAt < :threshold and e.id in :ids")
    int deleteExpiredBatch(@Param("status") AudioCommandStatus status,
                            @Param("threshold") OffsetDateTime threshold,
                            @Param("ids") List<UUID> ids);
}
