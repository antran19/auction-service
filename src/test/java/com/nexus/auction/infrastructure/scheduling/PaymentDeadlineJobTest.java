package com.nexus.auction.infrastructure.scheduling;

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
class PaymentDeadlineJobTest {

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

    @Autowired private PaymentDeadlineJob job;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OutboxJpaRepository outboxJpaRepository;

    private UUID seedEndedAuctionPastDeadline() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO auctions (id, product_id, seller_id, starting_price, bid_increment,
                                       current_highest_bid, current_highest_bidder_id, status,
                                       start_time, end_time, winner_id, final_price, payment_deadline,
                                       payment_timeout_emitted)
                VALUES (?, ?, 'seller-1', 100.00, 10.00, 150.00, 'bidder-1', 'ENDED',
                        now() - interval '2 hours', now() - interval '1 hour', 'bidder-1', 150.00,
                        now() - interval '1 minute', false)
                """, id, UUID.randomUUID());
        return id;
    }

    // Scoped to one auction's aggregateId — the 3 tests in this class share the same Spring
    // context/database with no rollback between them, so a global eventType count would
    // accumulate rows left behind by sibling tests (the same issue found and fixed in
    // AuctionLifecycleJobTest).
    private long paymentTimeoutEventCount(UUID auctionId) {
        return outboxJpaRepository.findAll().stream()
                .filter(row -> row.getAggregateId().equals(auctionId.toString()))
                .filter(row -> row.getEventType().equals("AuctionPaymentTimeout")).count();
    }

    @Test
    void run_emitsPaymentTimeoutForAWinnerPastTheDeadline() {
        UUID id = seedEndedAuctionPastDeadline();

        job.run();

        Boolean emitted = jdbcTemplate.queryForObject(
                "SELECT payment_timeout_emitted FROM auctions WHERE id = ?", Boolean.class, id);
        assertThat(emitted).isTrue();
        assertThat(paymentTimeoutEventCount(id)).isEqualTo(1);
    }

    @Test
    void run_doesNotDoubleEmitOnOverlappingPolls() {
        UUID id = seedEndedAuctionPastDeadline();

        job.run();
        job.run();

        assertThat(paymentTimeoutEventCount(id)).isEqualTo(1);
    }

    @Test
    void run_doesNotEmitForAnAuctionStillWithinTheDeadline() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO auctions (id, product_id, seller_id, starting_price, bid_increment,
                                       current_highest_bid, current_highest_bidder_id, status,
                                       start_time, end_time, winner_id, final_price, payment_deadline,
                                       payment_timeout_emitted)
                VALUES (?, ?, 'seller-1', 100.00, 10.00, 150.00, 'bidder-1', 'ENDED',
                        now() - interval '2 hours', now() - interval '1 hour', 'bidder-1', 150.00,
                        now() + interval '23 hours', false)
                """, id, UUID.randomUUID());

        job.run();

        assertThat(paymentTimeoutEventCount(id)).isZero();
    }
}
