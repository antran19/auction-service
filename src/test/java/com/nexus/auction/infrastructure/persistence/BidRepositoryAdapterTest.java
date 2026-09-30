package com.nexus.auction.infrastructure.persistence;

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
    void findByAuctionId_returnsEmptyForMalformedId() {
        assertThat(adapter.findByAuctionId("not-a-uuid", 0, 10)).isEmpty();
    }
}
