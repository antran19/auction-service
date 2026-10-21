package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.infrastructure.persistence.entity.OutboxJpaEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxJpaRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auction_db").withUsername("nexus").withPassword("nexus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private OutboxJpaRepository repository;
    @Autowired private TestEntityManager entityManager;

    private OutboxJpaEntity row(Instant createdAt, Instant publishedAtOrNull) {
        OutboxJpaEntity row = new OutboxJpaEntity(UUID.randomUUID(), "agg-1", "SomethingHappened", "{}", createdAt);
        if (publishedAtOrNull != null) {
            row.markPublished(publishedAtOrNull);
        }
        return entityManager.persist(row);
    }

    // Only a row that is BOTH published and older than the cutoff goes. An old row that was never
    // published is an event still waiting to be sent; deleting it would lose the event.
    @Test
    void deletePublishedBefore_removesOnlyOldPublishedRows() {
        Instant now = Instant.now();
        OutboxJpaEntity oldPublished = row(now.minus(10, ChronoUnit.DAYS), now.minus(8, ChronoUnit.DAYS));
        OutboxJpaEntity recentPublished = row(now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        OutboxJpaEntity oldUnpublished = row(now.minus(30, ChronoUnit.DAYS), null);
        entityManager.flush();

        int removed = repository.deletePublishedBefore(now.minus(7, ChronoUnit.DAYS));

        assertThat(removed).isEqualTo(1);
        assertThat(repository.findAll()).extracting(OutboxJpaEntity::getId)
                .containsExactlyInAnyOrder(recentPublished.getId(), oldUnpublished.getId())
                .doesNotContain(oldPublished.getId());
    }
}
