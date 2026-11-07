package com.nexus.auction.infrastructure.persistence;

import com.nexus.auction.application.port.out.BidderAuctionSummary;
import com.nexus.auction.domain.model.Bid;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(BidRepositoryAdapter.class)
class BidRepositoryAdapterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auction_db").withUsername("nexus").withPassword("nexus");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private BidRepositoryAdapter adapter;
    @Autowired private JdbcTemplate jdbcTemplate;

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
    void saveThenFindByAuctionId_returnsNewestFirst() {
        String auctionId = seedAuction();
        Bid first = Bid.create(auctionId, "bidder-1", new BigDecimal("100.00"));
        adapter.save(first);
        Bid second = Bid.create(auctionId, "bidder-2", new BigDecimal("110.00"));
        adapter.save(second);

        List<Bid> bids = adapter.findByAuctionId(auctionId, 0, 10);

        assertThat(bids).hasSize(2);
        assertThat(bids.get(0).getBidderId()).isEqualTo("bidder-2");
    }

    @Test
    void findAuctionsBidOnBy_givesOneRowPerAuction_withMyHighestBid_mostRecentlyActiveFirst() throws InterruptedException {
        String older = seedAuction();
        String newer = seedAuction();
        adapter.save(Bid.create(older, "me", new BigDecimal("100.00")));
        adapter.save(Bid.create(older, "me", new BigDecimal("130.00")));
        adapter.save(Bid.create(older, "other", new BigDecimal("200.00")));
        Thread.sleep(5);
        adapter.save(Bid.create(newer, "me", new BigDecimal("110.00")));

        List<BidderAuctionSummary> rows = adapter.findAuctionsBidOnBy("me", 0, 10);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).auctionId()).isEqualTo(newer);
        assertThat(rows.get(0).myHighestBid()).isEqualByComparingTo("110.00");
        assertThat(rows.get(1).auctionId()).isEqualTo(older);
        // only my own bids count: the other person's 200 is not mine
        assertThat(rows.get(1).myHighestBid()).isEqualByComparingTo("130.00");
    }

    @Test
    void findAuctionsBidOnBy_isPaged_andEmptyForSomeoneWhoNeverBid() throws InterruptedException {
        String first = seedAuction();
        String second = seedAuction();
        adapter.save(Bid.create(first, "me", new BigDecimal("100.00")));
        Thread.sleep(5);
        adapter.save(Bid.create(second, "me", new BigDecimal("100.00")));

        assertThat(adapter.findAuctionsBidOnBy("me", 0, 1)).extracting(BidderAuctionSummary::auctionId)
                .containsExactly(second);
        assertThat(adapter.findAuctionsBidOnBy("me", 1, 1)).extracting(BidderAuctionSummary::auctionId)
                .containsExactly(first);
        assertThat(adapter.findAuctionsBidOnBy("nobody", 0, 10)).isEmpty();
    }

    @Test
    void findByAuctionId_returnsEmptyForMalformedId() {
        assertThat(adapter.findByAuctionId("not-a-uuid", 0, 10)).isEmpty();
    }
}
