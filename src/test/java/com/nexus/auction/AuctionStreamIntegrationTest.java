package com.nexus.auction;

import com.nexus.common.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// The live stream on the whole application with a real PostgreSQL: a client keeps a connection
// open, someone bids over HTTP, and the client must receive that bid.
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuctionStreamIntegrationTest {

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

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String seedAuction() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO auctions (id, product_id, seller_id, starting_price, bid_increment, status,
                                       start_time, end_time)
                VALUES (?, ?, 'seller-1', 100.00, 10.00, 'ACTIVE', now() - interval '1 hour', now() + interval '1 hour')
                """, id, UUID.randomUUID());
        return id.toString();
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> bid(String auctionId, String user, int amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtTokenProvider.generateToken(user, "BUYER", List.of("AUCTION.BID"), "TRUSTED"));
        return rest.exchange("/api/v1/auctions/" + auctionId + "/bids", HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", amount), headers), Map.class);
    }

    private HttpResponse<java.io.InputStream> open(String auctionId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/api/v1/auctions/" + auctionId + "/stream")).header("Accept", "text/event-stream").build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    @Test
    void aWatcher_receivesTheBidsPlacedWhileTheStreamIsOpen() throws Exception {
        String auctionId = seedAuction();
        HttpResponse<java.io.InputStream> response = open(auctionId);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()));
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        Thread readerThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } catch (Exception ignored) {
                // The stream is closed when the test ends.
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();

        assertThat(bid(auctionId, "bidder-1", 120).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String eventLine = null;
        String dataLine = null;
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline && dataLine == null) {
            String line = lines.poll(500, TimeUnit.MILLISECONDS);
            if (line == null) {
                continue;
            }
            if (line.startsWith("event:")) {
                eventLine = line;
            } else if (line.startsWith("data:")) {
                dataLine = line;
            }
        }
        response.body().close();

        assertThat(eventLine).isEqualTo("event:bid");
        assertThat(dataLine).contains("\"bidderId\":\"bidder-1\"").contains("\"amount\":120");
        assertThat(dataLine).contains("\"auctionId\":\"" + auctionId + "\"");
    }

    @Test
    void aBidThatIsRefused_isNotSentToTheWatchers() throws Exception {
        String auctionId = seedAuction();
        HttpResponse<java.io.InputStream> response = open(auctionId);
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()));
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        Thread readerThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            } catch (Exception ignored) {
                // The stream is closed when the test ends.
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();

        assertThat(bid(auctionId, "bidder-1", 50).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Thread.sleep(1_000);
        response.body().close();

        assertThat(lines).noneMatch(line -> line.startsWith("event:") || line.startsWith("data:"));
    }

    @SuppressWarnings("rawtypes")
    @Test
    void streamingAnUnknownAuction_answersNotFoundAndOpensNothing() {
        ResponseEntity<Map> response = rest.exchange("/api/v1/auctions/" + UUID.randomUUID() + "/stream",
                HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
