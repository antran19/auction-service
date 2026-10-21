package com.nexus.auction.infrastructure.messaging;

import com.nexus.auction.infrastructure.persistence.OutboxJpaRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxCleanupJobTest {

    private final OutboxJpaRepository repository = mock(OutboxJpaRepository.class);
    private final OutboxCleanupJob job = new OutboxCleanupJob(repository, 7);

    @Test
    void cleanup_deletesRowsPublishedBeforeTheRetentionCutoff() {
        Instant now = Instant.parse("2026-10-21T10:00:00Z");
        when(repository.deletePublishedBefore(Instant.parse("2026-10-14T10:00:00Z"))).thenReturn(3);

        assertThat(job.cleanupAt(now)).isEqualTo(3);

        verify(repository).deletePublishedBefore(Instant.parse("2026-10-14T10:00:00Z"));
    }

    @Test
    void cleanup_isHarmlessWhenNothingIsOldEnough() {
        when(repository.deletePublishedBefore(Instant.parse("2026-10-14T10:00:00Z"))).thenReturn(0);

        assertThat(job.cleanupAt(Instant.parse("2026-10-21T10:00:00Z"))).isZero();
    }
}
