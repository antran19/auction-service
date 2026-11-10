package com.nexus.auction;

import com.nexus.auction.application.usecase.EndAuctionUseCase;
import com.nexus.common.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// The reserve price on the whole application with a real PostgreSQL: bids over HTTP, the auction running out
// of time, and what the end does to the row and to the events. Every auction starts at 100, increment 10.
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservePriceIntegrationTest {

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

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private EndAuctionUseCase endAuctionUseCase;

    private String seedAuction(String seller, String reserve) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO auctions (id, product_id, seller_id, starting_price, bid_increment, reserve_price, status,
                                       start_time, end_time)
                VALUES (?, ?, ?, 100.00, 10.00, CAST(? AS NUMERIC), 'ACTIVE',
                        now() - interval '1 hour', now() + interval '1 hour')
                """, id, UUID.randomUUID(), seller, reserve);
        return id.toString();
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> exchange(String path, HttpMethod method, Object body, String user, String... privileges) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (user != null) {
            headers.setBearerAuth(jwtTokenProvider.generateToken(user, "BUYER", List.of(privileges), "TRUSTED"));
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> bid(String auctionId, String bidder, int amount) {
        return exchange("/api/v1/auctions/" + auctionId + "/bids", HttpMethod.POST, Map.of("amount", amount), bidder,
                "AUCTION.BID");
    }

    // The auction runs out of time: the bids are already in, so only the clock moves.
    private void letTheTimeRunOut(String auctionId) {
        jdbcTemplate.update("UPDATE auctions SET end_time = now() - interval '1 minute' WHERE id = ?",
                UUID.fromString(auctionId));
    }

    private Map<String, Object> row(String auctionId) {
        return jdbcTemplate.queryForMap("SELECT status, winner_id, final_price, payment_deadline FROM auctions WHERE id = ?",
                UUID.fromString(auctionId));
    }

    private List<String> eventTypes(String auctionId) {
        return jdbcTemplate.queryForList("SELECT event_type FROM outbox WHERE aggregate_id = ?", String.class, auctionId);
    }

    @Test
    void bidsThatNeverReachTheReserve_endWithoutAWinner_andTheFailureEventGoesOut() {
        String auctionId = seedAuction("seller-" + UUID.randomUUID(), "300.00");
        bid(auctionId, "bidder-" + UUID.randomUUID(), 150);
        bid(auctionId, "bidder-" + UUID.randomUUID(), 200);
        letTheTimeRunOut(auctionId);

        endAuctionUseCase.end(auctionId);

        Map<String, Object> row = row(auctionId);
        assertThat(row.get("status")).isEqualTo("ENDED");
        assertThat(row.get("winner_id")).isNull();
        assertThat(row.get("final_price")).isNull();
        assertThat(row.get("payment_deadline")).isNull();
        assertThat(eventTypes(auctionId)).contains("AuctionEnded", "AuctionFailed").doesNotContain("AuctionWon");
    }

    @Test
    void aBidThatReachesTheReserve_wins_atThatPrice() {
        String auctionId = seedAuction("seller-" + UUID.randomUUID(), "300.00");
        String winner = "bidder-" + UUID.randomUUID();
        bid(auctionId, "bidder-" + UUID.randomUUID(), 150);
        bid(auctionId, winner, 300);
        letTheTimeRunOut(auctionId);

        endAuctionUseCase.end(auctionId);

        Map<String, Object> row = row(auctionId);
        assertThat(row.get("winner_id")).isEqualTo(winner);
        assertThat((BigDecimal) row.get("final_price")).isEqualByComparingTo("300.00");
        assertThat(eventTypes(auctionId)).contains("AuctionWon").doesNotContain("AuctionFailed");
    }

    @Test
    void anAuctionWithoutAReserve_stillSellsToAnyBid() {
        String auctionId = seedAuction("seller-" + UUID.randomUUID(), null);
        String winner = "bidder-" + UUID.randomUUID();
        bid(auctionId, winner, 100);
        letTheTimeRunOut(auctionId);

        endAuctionUseCase.end(auctionId);

        assertThat(row(auctionId).get("winner_id")).isEqualTo(winner);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void thePublicAuctionSaysWhetherTheReserveIsMet_butNeverTheAmount() {
        String auctionId = seedAuction("seller-" + UUID.randomUUID(), "300.00");

        Map before = (Map) exchange("/api/v1/auctions/" + auctionId, HttpMethod.GET, null, null).getBody().get("data");
        bid(auctionId, "bidder-" + UUID.randomUUID(), 150);
        Map below = (Map) exchange("/api/v1/auctions/" + auctionId, HttpMethod.GET, null, null).getBody().get("data");
        bid(auctionId, "bidder-" + UUID.randomUUID(), 300);
        Map met = (Map) exchange("/api/v1/auctions/" + auctionId, HttpMethod.GET, null, null).getBody().get("data");

        assertThat(before.get("hasReserve")).isEqualTo(true);
        assertThat(before.get("reserveMet")).isEqualTo(false);
        assertThat(below.get("reserveMet")).isEqualTo(false);
        assertThat(met.get("reserveMet")).isEqualTo(true);
        assertThat(before).doesNotContainKey("reservePrice");
        assertThat(met).doesNotContainKey("reservePrice");
        // the auction list is public too
        ResponseEntity<Map> list = exchange("/api/v1/auctions?size=100", HttpMethod.GET, null, null);
        assertThat(String.valueOf(list.getBody())).doesNotContain("reservePrice");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void onlyTheSellerSeesTheAmount_inTheirOwnList() {
        String seller = "seller-" + UUID.randomUUID();
        String auctionId = seedAuction(seller, "300.00");
        bid(auctionId, "bidder-" + UUID.randomUUID(), 150);

        ResponseEntity<Map> mine = exchange("/api/v1/auctions/mine/selling", HttpMethod.GET, null, seller, "AUCTION.CREATE");

        assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = (List<Map<String, Object>>) mine.getBody().get("data");
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(new BigDecimal(item.get("reservePrice").toString())).isEqualByComparingTo("300.00");
            assertThat(item.get("reserveMet")).isEqualTo(false);
        });
    }
}
