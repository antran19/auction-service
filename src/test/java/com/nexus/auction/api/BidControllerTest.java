package com.nexus.auction.api;

import com.nexus.auction.api.mapper.AuctionApiMapperImpl;
import com.nexus.auction.application.usecase.BidResult;
import com.nexus.auction.application.usecase.GetBidHistoryUseCase;
import com.nexus.auction.application.usecase.PlaceBidUseCase;
import com.nexus.auction.infrastructure.config.SecurityConfig;
import com.nexus.common.security.JwtAuthenticationFilter;
import com.nexus.common.security.JwtTokenProvider;
import com.nexus.common.security.PrivilegeAuthorizationAspect;
import com.nexus.common.web.GlobalExceptionHandler;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BidController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({GlobalExceptionHandler.class, AuctionApiMapperImpl.class, SecurityConfig.class,
        JwtAuthenticationFilter.class, PrivilegeAuthorizationAspect.class})
class BidControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PlaceBidUseCase placeBidUseCase;
    @MockBean private GetBidHistoryUseCase getBidHistoryUseCase;
    @MockBean private JwtTokenProvider jwtTokenProvider;

    @Test
    void placeBid_returns401WithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/auctions/a-id/bids")
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void placeBid_returns201AndUsesJwtSubjectAsBidder() throws Exception {
        when(jwtTokenProvider.isValid("good-token")).thenReturn(true);
        Claims claims = Jwts.claims().subject("bidder-id").add("privileges", List.of("AUCTION.BID")).build();
        when(jwtTokenProvider.parseClaims("good-token")).thenReturn(claims);
        when(placeBidUseCase.placeBid("a-id", "bidder-id", new BigDecimal("100.00"), null))
                .thenReturn(new BidResult("bid-id", "a-id", "bidder-id", new BigDecimal("100.00"), Instant.now()));

        mockMvc.perform(post("/api/v1/auctions/a-id/bids")
                        .header("Authorization", "Bearer good-token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.bidderId").value("bidder-id"));
    }

    @Test
    void placeBid_passesTrustLevelClaimFromTokenToUseCase() throws Exception {
        when(jwtTokenProvider.isValid("good-token")).thenReturn(true);
        Claims claims = Jwts.claims().subject("bidder-id").add("privileges", List.of("AUCTION.BID"))
                .add("trustLevel", "LOW").build();
        when(jwtTokenProvider.parseClaims("good-token")).thenReturn(claims);
        when(placeBidUseCase.placeBid("a-id", "bidder-id", new BigDecimal("100.00"), "LOW"))
                .thenThrow(new com.nexus.common.core.exception.ForbiddenException(
                        "INSUFFICIENT_TRUST_TO_BID", "Your reputation score is too low to place bids"));

        mockMvc.perform(post("/api/v1/auctions/a-id/bids")
                        .header("Authorization", "Bearer good-token")
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_TRUST_TO_BID"));
    }

    @Test
    void bidHistory_isPublicAndReturns200WithoutAToken() throws Exception {
        when(getBidHistoryUseCase.getHistory("a-id", 0, 20)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/auctions/a-id/bids"))
                .andExpect(status().isOk());
    }
}
