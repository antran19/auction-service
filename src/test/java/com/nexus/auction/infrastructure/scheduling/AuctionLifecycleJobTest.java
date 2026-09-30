package com.nexus.auction.infrastructure.scheduling;

import com.nexus.auction.application.usecase.EndAuctionUseCase;
import com.nexus.auction.application.usecase.StartAuctionUseCase;
import com.nexus.auction.infrastructure.persistence.OutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuctionLifecycleJobTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auction_db").withUsername("nexus").withPassword("nexus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired private AuctionLifecycleJob job;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OutboxJpaRepository outboxJpaRepository;

    private UUID seedAuction(String status, String startOffset, String endOffset,
                              String highestBidder, String highestBid) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO auctions (id, product_id, seller_id, starting_price, bid_increment,
                                       current_highest_bid, current_highest_bidder_id, status,
                                       start_time, end_time)
                VALUES (?, ?, 'seller-1', 100.00, 10.00, CAST(? AS NUMERIC), ?, ?, now() + (? || ' minutes')::interval,
                        now() + (? || ' minutes')::interval)
                """, id, UUID.randomUUID(), highestBid, highestBidder, status, startOffset, endOffset);
        return id;
    }

    // Scoped to one auction's aggregateId, not a global count — all 4 tests share the same
    // Spring context/database with no rollback between them, so a global eventType count
    // accumulates rows left behind by sibling tests run earlier in the same class.
    private long countByEventType(UUID auctionId, String eventType) {
        return outboxJpaRepository.findAll().stream()
                .filter(row -> row.getAggregateId().equals(auctionId.toString()))
                .filter(row -> row.getEventType().equals(eventType)).count();
    }

    @Test
    void run_transitionsPendingAuctionWhoseStartTimeHasPassedToActive() {
        UUID id = seedAuction("PENDING", "-10", "60", null, null);

        job.run();

        String status = jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, id);
        assertThat(status).isEqualTo("ACTIVE");
        assertThat(countByEventType(id, "AuctionStarted")).isEqualTo(1);
    }

    @Test
    void run_endsActiveAuctionWithABidAndRecordsTheWinner() {
        UUID id = seedAuction("ACTIVE", "-60", "-1", "bidder-1", "150.00");

        job.run();

        String status = jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, id);
        String winnerId = jdbcTemplate.queryForObject("SELECT winner_id FROM auctions WHERE id = ?", String.class, id);
        assertThat(status).isEqualTo("ENDED");
        assertThat(winnerId).isEqualTo("bidder-1");
        assertThat(countByEventType(id, "AuctionEnded")).isEqualTo(1);
        assertThat(countByEventType(id, "AuctionWon")).isEqualTo(1);
    }

    @Test
    void run_endsActiveAuctionWithNoBidsAsFailed() {
        UUID id = seedAuction("ACTIVE", "-60", "-1", null, null);

        job.run();

        String status = jdbcTemplate.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, id);
        assertThat(status).isEqualTo("ENDED");
        assertThat(countByEventType(id, "AuctionFailed")).isEqualTo(1);
    }

    @Test
    void run_isIdempotentAcrossOverlappingPolls() {
        UUID id = seedAuction("ACTIVE", "-60", "-1", "bidder-1", "150.00");

        job.run();
        job.run();

        // The second run's findActiveReadyToEnd query returns nothing (the auction is already
        // ENDED), so no duplicate AuctionEnded/AuctionWon event is emitted.
        assertThat(countByEventType(id, "AuctionEnded")).isEqualTo(1);
        assertThat(countByEventType(id, "AuctionWon")).isEqualTo(1);
    }
}
