package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.domain.model.AutoBid;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AutoBidRepositoryAdapter.class)
class AutoBidRepositoryAdapterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auction_db").withUsername("nexus").withPassword("nexus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private AutoBidRepositoryAdapter adapter;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    private String seedAuction() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO auctions (id, product_id, seller_id, starting_price, bid_increment, status,
                                       start_time, end_time)
                VALUES (?, ?, 'seller-1', 100.00, 10.00, 'ACTIVE', now(), now() + interval '1 hour')
                """, id, UUID.randomUUID());
        return id.toString();
    }

    @Test
    void saveThenFind_roundTripsTheAutoBid() {
        String auctionId = seedAuction();
        AutoBid saved = adapter.save(AutoBid.create(auctionId, "bidder-1", new BigDecimal("500.00")));
        entityManager.flush();
        entityManager.clear();

        AutoBid found = adapter.findByAuctionIdAndBidderId(auctionId, "bidder-1").orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getMaxAmount()).isEqualByComparingTo("500.00");
        assertThat(found.getAuctionId()).isEqualTo(auctionId);
    }

    @Test
    void savingTheRaisedAutoBid_updatesTheSameRow_andKeepsCreatedAt() throws InterruptedException {
        String auctionId = seedAuction();
        AutoBid first = adapter.save(AutoBid.create(auctionId, "bidder-1", new BigDecimal("500.00")));
        entityManager.flush();
        entityManager.clear();
        Thread.sleep(5);

        adapter.save(first.withMaxAmount(new BigDecimal("900.00")));
        entityManager.flush();
        entityManager.clear();

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM auto_bids WHERE auction_id = ?", Integer.class,
                UUID.fromString(auctionId))).isEqualTo(1);
        AutoBid found = adapter.findByAuctionIdAndBidderId(auctionId, "bidder-1").orElseThrow();
        assertThat(found.getMaxAmount()).isEqualByComparingTo("900.00");
        // PostgreSQL rounds to microseconds, a Java Instant can carry nanoseconds.
        assertThat(Duration.between(found.getCreatedAt(), first.getCreatedAt()).abs())
                .isLessThanOrEqualTo(Duration.of(1, ChronoUnit.MICROS));
        assertThat(found.getUpdatedAt()).isAfter(first.getCreatedAt());
    }

    @Test
    void findByAuctionId_returnsThatAuctionsAutoBids_oldestFirst() throws InterruptedException {
        String auctionId = seedAuction();
        String other = seedAuction();
        adapter.save(AutoBid.create(auctionId, "bidder-1", new BigDecimal("300.00")));
        Thread.sleep(5);
        adapter.save(AutoBid.create(auctionId, "bidder-2", new BigDecimal("700.00")));
        adapter.save(AutoBid.create(other, "bidder-3", new BigDecimal("999.00")));
        entityManager.flush();
        entityManager.clear();

        List<AutoBid> found = adapter.findByAuctionId(auctionId);

        assertThat(found).extracting(AutoBid::getBidderId).containsExactly("bidder-1", "bidder-2");
    }

    @Test
    void find_isEmptyWhenThereIsNone_orTheIdIsMalformed() {
        assertThat(adapter.findByAuctionIdAndBidderId(seedAuction(), "nobody")).isEmpty();
        assertThat(adapter.findByAuctionIdAndBidderId("not-a-uuid", "bidder-1")).isEmpty();
        assertThat(adapter.findByAuctionId("not-a-uuid")).isEmpty();
    }

    @Test
    void aSecondRowForTheSameBidderAndAuction_isRefusedByTheDatabase() {
        String auctionId = seedAuction();
        adapter.save(AutoBid.create(auctionId, "bidder-1", new BigDecimal("500.00")));
        entityManager.flush();

        adapter.save(AutoBid.create(auctionId, "bidder-1", new BigDecimal("600.00")));

        assertThatThrownBy(() -> entityManager.flush())
                .satisfies(e -> assertThat(rootMessage(e)).contains("uq_auto_bids_auction_bidder"));
    }

    @Test
    void aMaximumOfZeroOrLess_isRefusedByTheDatabase() {
        String auctionId = seedAuction();
        adapter.save(AutoBid.create(auctionId, "bidder-1", BigDecimal.ZERO));

        assertThatThrownBy(() -> entityManager.flush())
                .satisfies(e -> assertThat(rootMessage(e)).contains("chk_auto_bids_max_positive"));
    }

    @Test
    void anAutoBidForAnAuctionThatDoesNotExist_isRefusedByTheForeignKey() {
        adapter.save(AutoBid.create(UUID.randomUUID().toString(), "bidder-1", new BigDecimal("500.00")));

        assertThatThrownBy(() -> entityManager.flush())
                .satisfies(e -> assertThat(rootMessage(e)).contains("auto_bids_auction_id_fkey"));
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return String.valueOf(t.getMessage());
    }
}
