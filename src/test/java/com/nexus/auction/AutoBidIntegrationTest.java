package com.nexus.auction;

import com.nexus.auction.application.usecase.PlaceBidUseCase;
import com.nexus.auction.application.usecase.SetAutoBidUseCase;
import com.nexus.common.core.exception.ValidationException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// A standing maximum answering a bid, on the whole application with a real PostgreSQL: the HTTP
// request, the token, the row lock and the bids and auctions tables.
// Every auction here starts at 100 with an increment of 10.
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AutoBidIntegrationTest {

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
    @Autowired private PlaceBidUseCase placeBidUseCase;
    @Autowired private SetAutoBidUseCase setAutoBidUseCase;

    private String seedAuction() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO auctions (id, product_id, seller_id, starting_price, bid_increment, status,
                                       start_time, end_time)
                VALUES (?, ?, 'seller-1', 100.00, 10.00, 'ACTIVE', now() - interval '1 hour', now() + interval '1 hour')
                """, id, UUID.randomUUID());
        return id.toString();
    }

    private void seedAutoBid(String auctionId, String bidder, int max) {
        jdbcTemplate.update("INSERT INTO auto_bids (id, auction_id, bidder_id, max_amount) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), UUID.fromString(auctionId), bidder, new BigDecimal(max));
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> post(String auctionId, String user, int amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtTokenProvider.generateToken(user, "BUYER", List.of("AUCTION.BID"), "TRUSTED"));
        return rest.exchange("/api/v1/auctions/" + auctionId + "/bids", HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", amount), headers), Map.class);
    }

    private List<Map<String, Object>> bidsOf(String auctionId) {
        return jdbcTemplate.queryForList("SELECT bidder_id, amount FROM bids WHERE auction_id = ?",
                UUID.fromString(auctionId));
    }

    private Map<String, Object> auctionRow(String auctionId) {
        return jdbcTemplate.queryForMap("SELECT current_highest_bid, current_highest_bidder_id FROM auctions WHERE id = ?",
                UUID.fromString(auctionId));
    }

    @SuppressWarnings("rawtypes")
    @Test
    void aStandingMaximum_answersAManualBidWithOneIncrementMore() {
        String auctionId = seedAuction();
        seedAutoBid(auctionId, "an", 500);

        ResponseEntity<Map> response = post(auctionId, "binh", 150);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(bidsOf(auctionId)).extracting(b -> b.get("bidder_id")).containsExactlyInAnyOrder("binh", "an");
        Map<String, Object> auction = auctionRow(auctionId);
        assertThat(auction.get("current_highest_bidder_id")).isEqualTo("an");
        assertThat((BigDecimal) auction.get("current_highest_bid")).isEqualByComparingTo("160.00");
    }

    @Test
    void aManualBidAboveTheStandingMaximum_wins_andTheMaximumIsNotUsed() {
        String auctionId = seedAuction();
        seedAutoBid(auctionId, "an", 500);

        post(auctionId, "binh", 520);

        assertThat(bidsOf(auctionId)).hasSize(1);
        assertThat(auctionRow(auctionId).get("current_highest_bidder_id")).isEqualTo("binh");
    }

    @Test
    void aManualBidLessThanOneIncrementBelowTheMaximum_isNotAnswered() {
        String auctionId = seedAuction();
        seedAutoBid(auctionId, "an", 500);

        post(auctionId, "binh", 495);

        assertThat(bidsOf(auctionId)).hasSize(1);
        assertThat(auctionRow(auctionId).get("current_highest_bidder_id")).isEqualTo("binh");
    }

    @Test
    void theHistoryOfAnAnsweredBid_isAlwaysOneFullIncrementApart() {
        String auctionId = seedAuction();
        seedAutoBid(auctionId, "an", 500);
        seedAutoBid(auctionId, "chi", 300);

        post(auctionId, "binh", 120);

        List<BigDecimal> amounts = jdbcTemplate.queryForList(
                "SELECT amount FROM bids WHERE auction_id = ? ORDER BY amount", BigDecimal.class, UUID.fromString(auctionId));
        for (int i = 1; i < amounts.size(); i++) {
            assertThat(amounts.get(i).subtract(amounts.get(i - 1))).isGreaterThanOrEqualTo(new BigDecimal(10));
        }
        // An (500) beats Chi (300): An pays 310.
        assertThat(auctionRow(auctionId).get("current_highest_bidder_id")).isEqualTo("an");
        assertThat((BigDecimal) auctionRow(auctionId).get("current_highest_bid")).isEqualByComparingTo("310.00");
    }

    // Several people bid by hand at the same moment against a standing maximum. The row lock makes
    // them queue, so the maximum answers each accepted bid and the end state does not depend on
    // the order: An (maximum 500) leads, one increment above the highest manual bid.
    @Test
    void severalManualBidsAtTheSameMoment_againstAStandingMaximum_endWithTheMaximumLeading() throws Exception {
        String auctionId = seedAuction();
        seedAutoBid(auctionId, "an", 500);
        int people = 6;
        ExecutorService pool = Executors.newFixedThreadPool(people);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < people; i++) {
            String bidder = "bidder-" + i;
            BigDecimal amount = new BigDecimal(150 + i * 20);
            results.add(pool.submit(() -> {
                go.await();
                try {
                    placeBidUseCase.placeBid(auctionId, bidder, amount, "TRUSTED");
                } catch (ValidationException e) {
                    // A bid that arrives after a better one is refused: expected.
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : results) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        BigDecimal highestManual = bidsOf(auctionId).stream().filter(b -> !"an".equals(b.get("bidder_id")))
                .map(b -> (BigDecimal) b.get("amount")).max(BigDecimal::compareTo).orElseThrow();
        assertThat(auctionRow(auctionId).get("current_highest_bidder_id")).isEqualTo("an");
        assertThat((BigDecimal) auctionRow(auctionId).get("current_highest_bid"))
                .isEqualByComparingTo(highestManual.add(new BigDecimal(10)));
    }

    // ---- setting a maximum places bids straight away

    private void setMax(String auctionId, String bidder, int max) {
        setAutoBidUseCase.setAutoBid(auctionId, bidder, new BigDecimal(max), "TRUSTED");
    }

    private List<String> historyOf(String auctionId) {
        return jdbcTemplate.queryForList("SELECT bidder_id || ':' || trunc(amount)::text FROM bids WHERE auction_id = ? ORDER BY amount",
                String.class, UUID.fromString(auctionId));
    }

    @Test
    void aFirstMaximum_placesTheStartingPriceBid_andLeads() {
        String auctionId = seedAuction();

        setMax(auctionId, "an", 500);

        assertThat(historyOf(auctionId)).containsExactly("an:100");
        assertThat(auctionRow(auctionId).get("current_highest_bidder_id")).isEqualTo("an");
        assertThat((BigDecimal) auctionRow(auctionId).get("current_highest_bid")).isEqualByComparingTo("100.00");
    }

    @Test
    void aLowerMaximum_isBeatenByTheLeader_andTheLeaderPaysOneIncrementAboveIt() {
        String auctionId = seedAuction();
        setMax(auctionId, "an", 500);

        setMax(auctionId, "binh", 300);

        assertThat(historyOf(auctionId)).containsExactly("an:100", "binh:300", "an:310");
        assertThat(auctionRow(auctionId).get("current_highest_bidder_id")).isEqualTo("an");
    }

    @Test
    void aHigherMaximumWithinOneIncrement_wins_atItsOwnMaximum_andTheHistoryStaysOneIncrementApart() {
        String auctionId = seedAuction();
        setMax(auctionId, "an", 500);

        setMax(auctionId, "chi", 505);

        assertThat(historyOf(auctionId)).containsExactly("an:100", "an:495", "chi:505");
        assertThat(auctionRow(auctionId).get("current_highest_bidder_id")).isEqualTo("chi");
        assertThat((BigDecimal) auctionRow(auctionId).get("current_highest_bid")).isEqualByComparingTo("505.00");
    }

    @Test
    void raisingTheOwnMaximumWhileLeading_changesNothingVisible() {
        String auctionId = seedAuction();
        setMax(auctionId, "an", 500);

        setMax(auctionId, "an", 800);

        assertThat(historyOf(auctionId)).containsExactly("an:100");
        assertThat((BigDecimal) auctionRow(auctionId).get("current_highest_bid")).isEqualByComparingTo("100.00");
    }

    // ---- the endpoint, over HTTP

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> put(String auctionId, String user, Object maxAmount, boolean withToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (withToken) {
            headers.setBearerAuth(jwtTokenProvider.generateToken(user, "BUYER", List.of("AUCTION.BID"), "TRUSTED"));
        }
        return rest.exchange("/api/v1/auctions/" + auctionId + "/auto-bid", HttpMethod.PUT,
                new HttpEntity<>(Map.of("maxAmount", maxAmount), headers), Map.class);
    }

    @SuppressWarnings("rawtypes")
    @Test
    void putAutoBid_storesTheMaximum_andPlacesTheStartingPriceBid() {
        String auctionId = seedAuction();

        ResponseEntity<Map> response = put(auctionId, "an", 500, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map) response.getBody().get("data")).get("bidderId")).isEqualTo("an");
        assertThat(historyOf(auctionId)).containsExactly("an:100");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM auto_bids WHERE auction_id = ?", Integer.class,
                UUID.fromString(auctionId))).isEqualTo(1);
    }

    @SuppressWarnings("rawtypes")
    @Test
    void putAutoBid_answersALaterManualBidOverHttp() {
        String auctionId = seedAuction();
        put(auctionId, "an", 500, true);

        post(auctionId, "binh", 150);

        assertThat(auctionRow(auctionId).get("current_highest_bidder_id")).isEqualTo("an");
        assertThat((BigDecimal) auctionRow(auctionId).get("current_highest_bid")).isEqualByComparingTo("160.00");
    }

    @SuppressWarnings("rawtypes")
    @Test
    void putAutoBid_refusesToLowerTheMaximum_with409() {
        String auctionId = seedAuction();
        put(auctionId, "an", 500, true);

        ResponseEntity<Map> response = put(auctionId, "an", 300, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((Map) response.getBody().get("error")).get("code")).isEqualTo("AUTO_BID_CANNOT_BE_LOWERED");
        assertThat(jdbcTemplate.queryForObject("SELECT max_amount FROM auto_bids WHERE auction_id = ?", BigDecimal.class,
                UUID.fromString(auctionId))).isEqualByComparingTo("500.00");
    }

    @SuppressWarnings("rawtypes")
    @Test
    void putAutoBid_loweringIsRefusedAs409_evenWhenThePriceIsAlreadyAboveTheNewValue() {
        String auctionId = seedAuction();
        put(auctionId, "an", 500, true);
        put(auctionId, "chi", 505, true);

        // The price is now 505, so 300 is also below the minimum bid; the person must still be told
        // that a maximum cannot be lowered.
        ResponseEntity<Map> response = put(auctionId, "an", 300, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((Map) response.getBody().get("error")).get("code")).isEqualTo("AUTO_BID_CANNOT_BE_LOWERED");
    }

    @SuppressWarnings("rawtypes")
    @Test
    void putAutoBid_withoutAToken_isRefused() {
        String auctionId = seedAuction();

        ResponseEntity<Map> response = put(auctionId, "an", 500, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(historyOf(auctionId)).isEmpty();
    }

    @SuppressWarnings("rawtypes")
    @Test
    void putAutoBid_theSellerCannotSetOneOnTheirOwnAuction() {
        String auctionId = seedAuction();

        ResponseEntity<Map> response = put(auctionId, "seller-1", 500, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((Map) response.getBody().get("error")).get("code")).isEqualTo("SELLER_CANNOT_BID_OWN_AUCTION");
    }
}
