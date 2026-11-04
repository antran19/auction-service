package com.nexus.auction.api;

import com.nexus.auction.application.exception.AuctionNotFoundException;
import com.nexus.auction.application.usecase.AutoBidResult;
import com.nexus.auction.application.usecase.SetAutoBidUseCase;
import com.nexus.auction.infrastructure.config.SecurityConfig;
import com.nexus.common.core.exception.ConflictException;
import com.nexus.common.core.exception.ForbiddenException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AutoBidController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class,
        PrivilegeAuthorizationAspect.class})
class AutoBidControllerTest {

    private static final String URL = "/api/v1/auctions/auction-1/auto-bid";

    @Autowired private MockMvc mockMvc;

    @MockBean private SetAutoBidUseCase setAutoBidUseCase;
    @MockBean private JwtTokenProvider jwtTokenProvider;

    private void authenticateAs(String subject, String... privileges) {
        when(jwtTokenProvider.isValid("good-token")).thenReturn(true);
        Claims claims = Jwts.claims().subject(subject).add("privileges", List.of(privileges)).build();
        when(jwtTokenProvider.parseClaims("good-token")).thenReturn(claims);
    }

    @Test
    void setAutoBid_returns401WithoutToken() throws Exception {
        mockMvc.perform(put(URL).contentType(APPLICATION_JSON).content("{\"maxAmount\":500}"))
                .andExpect(status().isUnauthorized());
        verify(setAutoBidUseCase, never()).setAutoBid(anyString(), anyString(), any(), any());
    }

    @Test
    void setAutoBid_returns403WithoutTheBidPrivilege() throws Exception {
        authenticateAs("bidder-1", "AUCTION.VIEW");

        mockMvc.perform(put(URL).header("Authorization", "Bearer good-token")
                        .contentType(APPLICATION_JSON).content("{\"maxAmount\":500}"))
                .andExpect(status().isForbidden());
        verify(setAutoBidUseCase, never()).setAutoBid(anyString(), anyString(), any(), any());
    }

    @Test
    void setAutoBid_returns200_andTakesTheBidderFromTheToken() throws Exception {
        authenticateAs("bidder-1", "AUCTION.BID");
        when(setAutoBidUseCase.setAutoBid(eq("auction-1"), eq("bidder-1"), any(), any()))
                .thenReturn(new AutoBidResult("ab-1", "auction-1", "bidder-1", new BigDecimal("500.00"), Instant.now()));

        // A bidderId in the body must be ignored.
        mockMvc.perform(put(URL).header("Authorization", "Bearer good-token")
                        .contentType(APPLICATION_JSON).content("{\"maxAmount\":500,\"bidderId\":\"attacker\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bidderId").value("bidder-1"))
                .andExpect(jsonPath("$.data.maxAmount").value(500.00))
                .andExpect(jsonPath("$.data.auctionId").value("auction-1"));
        verify(setAutoBidUseCase).setAutoBid(eq("auction-1"), eq("bidder-1"), any(), any());
    }

    @Test
    void setAutoBid_returns400_whenTheMaximumIsMissingOrNotPositive() throws Exception {
        authenticateAs("bidder-1", "AUCTION.BID");

        mockMvc.perform(put(URL).header("Authorization", "Bearer good-token")
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(put(URL).header("Authorization", "Bearer good-token")
                        .contentType(APPLICATION_JSON).content("{\"maxAmount\":0}"))
                .andExpect(status().isBadRequest());
        verify(setAutoBidUseCase, never()).setAutoBid(anyString(), anyString(), any(), any());
    }

    @Test
    void setAutoBid_returns409WithTheReason_whenTheMaximumWouldBeLowered() throws Exception {
        authenticateAs("bidder-1", "AUCTION.BID");
        when(setAutoBidUseCase.setAutoBid(anyString(), anyString(), any(), any()))
                .thenThrow(new ConflictException("AUTO_BID_CANNOT_BE_LOWERED", "cannot be lowered"));

        mockMvc.perform(put(URL).header("Authorization", "Bearer good-token")
                        .contentType(APPLICATION_JSON).content("{\"maxAmount\":300}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("AUTO_BID_CANNOT_BE_LOWERED"));
    }

    @Test
    void setAutoBid_returns403_whenTheReputationIsTooLow() throws Exception {
        authenticateAs("bidder-1", "AUCTION.BID");
        when(setAutoBidUseCase.setAutoBid(anyString(), anyString(), any(), any()))
                .thenThrow(new ForbiddenException("INSUFFICIENT_TRUST_TO_BID", "too low"));

        mockMvc.perform(put(URL).header("Authorization", "Bearer good-token")
                        .contentType(APPLICATION_JSON).content("{\"maxAmount\":500}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_TRUST_TO_BID"));
    }

    @Test
    void setAutoBid_returns404_forAnUnknownAuction() throws Exception {
        authenticateAs("bidder-1", "AUCTION.BID");
        when(setAutoBidUseCase.setAutoBid(anyString(), anyString(), any(), any()))
                .thenThrow(new AuctionNotFoundException("auction-1"));

        mockMvc.perform(put(URL).header("Authorization", "Bearer good-token")
                        .contentType(APPLICATION_JSON).content("{\"maxAmount\":500}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AUCTION_NOT_FOUND"));
    }
}
