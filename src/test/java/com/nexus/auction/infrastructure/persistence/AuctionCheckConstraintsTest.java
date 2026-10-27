package com.nexus.auction.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// V8: the database itself refuses values the application already forbids.
// No surrounding transaction: PostgreSQL aborts a transaction after a failed statement, so each
// insert must commit (or fail) on its own for a test to try several bad rows in a row.
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuctionCheckConstraintsTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auction_db").withUsername("nexus").withPassword("nexus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private JdbcTemplate jdbcTemplate;

    private void insertAuction(UUID id, String status, String startOffset, String endOffset,
                               String startingPrice, String bidIncrement) {
        jdbcTemplate.update("""
                INSERT INTO auctions (id, product_id, seller_id, starting_price, bid_increment, status,
                                       start_time, end_time)
                VALUES (?, ?, 'seller-1', CAST(? AS NUMERIC), CAST(? AS NUMERIC), ?,
                        now() + (? || ' minutes')::interval, now() + (? || ' minutes')::interval)
                """, id, UUID.randomUUID(), startingPrice, bidIncrement, status, startOffset, endOffset);
    }

    private void insertValidAuction(UUID id) {
        insertAuction(id, "ACTIVE", "-60", "60", "100.00", "10.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "ACTIVE", "ENDED", "CANCELLED"})
    void everyAuctionStatusTheApplicationKnows_isAccepted(String status) {
        assertThatCode(() -> insertAuction(UUID.randomUUID(), status, "-60", "60", "100.00", "10.00"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC", "FAILED", "active", ""})
    void aStatusOutsideTheEnum_isRejected(String status) {
        assertThatThrownBy(() -> insertAuction(UUID.randomUUID(), status, "-60", "60", "100.00", "10.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_auctions_status");
    }

    @Test
    void anAuctionThatEndsBeforeItStarts_isRejected() {
        assertThatThrownBy(() -> insertAuction(UUID.randomUUID(), "PENDING", "60", "30", "100.00", "10.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_auctions_window");
    }

    @Test
    void anAuctionThatEndsExactlyWhenItStarts_isRejected() {
        assertThatThrownBy(() -> insertAuction(UUID.randomUUID(), "PENDING", "60", "60", "100.00", "10.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_auctions_window");
    }

    @Test
    void aStartingPriceOfZero_isRejected() {
        assertThatThrownBy(() -> insertAuction(UUID.randomUUID(), "PENDING", "10", "70", "0", "10.00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_auctions_prices_positive");
    }

    @Test
    void aBidIncrementOfZeroOrLess_isRejected() {
        assertThatThrownBy(() -> insertAuction(UUID.randomUUID(), "PENDING", "10", "70", "100.00", "0"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_auctions_prices_positive");
        assertThatThrownBy(() -> insertAuction(UUID.randomUUID(), "PENDING", "10", "70", "100.00", "-5"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_auctions_prices_positive");
    }

    @Test
    void aBidWithAPositiveAmount_isAccepted_andZeroOrNegativeIsRejected() {
        UUID auctionId = UUID.randomUUID();
        insertValidAuction(auctionId);

        assertThatCode(() -> insertBid(auctionId, "0.01")).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertBid(auctionId, "0"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_bids_amount_positive");
        assertThatThrownBy(() -> insertBid(auctionId, "-10"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_bids_amount_positive");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM bids WHERE auction_id = ?", Integer.class, auctionId)).isEqualTo(1);
    }

    private void insertBid(UUID auctionId, String amount) {
        jdbcTemplate.update("""
                INSERT INTO bids (id, auction_id, bidder_id, amount)
                VALUES (?, ?, 'bidder-1', CAST(? AS NUMERIC))
                """, UUID.randomUUID(), auctionId, amount);
    }
}
