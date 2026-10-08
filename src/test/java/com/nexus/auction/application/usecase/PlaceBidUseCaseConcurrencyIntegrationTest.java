package com.nexus.auction.application.usecase;

import com.nexus.auction.application.port.out.AuctionRepositoryPort;
import com.nexus.common.core.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PlaceBidUseCaseConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auction_db").withUsername("nexus").withPassword("nexus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("eureka.client.enabled", () -> "false");
        // Testcontainers' default connection pool (HikariCP, default max 10) would deadlock this
        // test: 20 threads each hold a connection blocked on the row lock, so a pool smaller than
        // the thread count starves the very threads waiting to acquire the lock.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "25");
    }

    @Autowired private PlaceBidUseCase placeBidUseCase;
    @Autowired private AuctionRepositoryPort auctionRepositoryPort;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void placeBid_underConcurrentIdenticalBids_exactlyOneWinsAndNoBidIsLost()
            throws InterruptedException, ExecutionException, TimeoutException {
        UUID auctionId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO auctions (id, product_id, seller_id, starting_price, bid_increment, status,
                                       start_time, end_time)
                VALUES (?, ?, 'seller-1', 100.00, 10.00, 'ACTIVE', now() - interval '1 hour', now() + interval '1 hour')
                """, auctionId, UUID.randomUUID());

        int threadCount = 20;
        BigDecimal contestedAmount = new BigDecimal("500.00");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        List<Throwable> unexpectedFailures = new CopyOnWriteArrayList<>();
        List<Future<?>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            String bidderId = "bidder-" + i;
            futures.add(executor.submit(() -> {
                try {
                    startGate.await();
                    placeBidUseCase.placeBid(auctionId.toString(), bidderId, contestedAmount, "TRUSTED");
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ValidationException expectedForEveryLosingBidder) {
                    // All 20 threads bid the SAME amount; the moment one commits, every
                    // other thread re-reads under the lock and finds that amount no longer
                    // strictly above the new highest bid — a ValidationException, not any
                    // other exception type. Anything else here (a lock timeout, a DB error,
                    // ConflictException from a status/end-time check that shouldn't apply
                    // to an ACTIVE auction) is a real problem this test must not swallow.
                } catch (Exception unexpected) {
                    unexpectedFailures.add(unexpected);
                }
            }));
        }
        startGate.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(unexpectedFailures)
                .as("every losing bidder must fail with ValidationException specifically, nothing else")
                .isEmpty();
        assertThat(successCount.get())
                .as("exactly one of the identical concurrent bids must be accepted")
                .isEqualTo(1);

        assertThat(auctionRepositoryPort.findById(auctionId.toString()).orElseThrow().getCurrentHighestBid())
                .as("the auction's recorded highest bid must equal the one bid that actually won")
                .isEqualByComparingTo(contestedAmount);

        Long bidRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bids WHERE auction_id = ?", Long.class, auctionId);
        assertThat(bidRows)
                .as("no duplicate/lost-update bid row may exist at the contested amount")
                .isEqualTo(1L);
    }
}
