package com.nexus.auction.api;

import com.nexus.auction.application.usecase.ListMyBiddingUseCase;
import com.nexus.auction.application.usecase.MyBiddingItem;
import com.nexus.auction.infrastructure.config.SecurityConfig;
import com.nexus.common.core.FieldError;
import com.nexus.common.core.exception.ValidationException;
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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MyBiddingController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class,
        PrivilegeAuthorizationAspect.class})
class MyBiddingControllerTest {

    private static final String URL = "/api/v1/auctions/mine/bidding";

    @Autowired private MockMvc mockMvc;

    @MockBean private ListMyBiddingUseCase listMyBiddingUseCase;
    @MockBean private JwtTokenProvider jwtTokenProvider;

    private void authenticateAs(String subject, String... privileges) {
        when(jwtTokenProvider.isValid("good-token")).thenReturn(true);
        Claims claims = Jwts.claims().subject(subject).add("privileges", List.of(privileges)).build();
        when(jwtTokenProvider.parseClaims("good-token")).thenReturn(claims);
    }

    @Test
    void returns401WithoutToken() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verify(listMyBiddingUseCase, never()).list(anyString(), anyInt(), anyInt());
    }

    @Test
    void returns403WithoutTheBidPrivilege() throws Exception {
        authenticateAs("bidder-1", "AUCTION.VIEW");

        mockMvc.perform(get(URL).header("Authorization", "Bearer good-token")).andExpect(status().isForbidden());
        verify(listMyBiddingUseCase, never()).list(anyString(), anyInt(), anyInt());
    }

    @Test
    void returnsTheListOfThePersonInTheToken_withDefaultPaging() throws Exception {
        authenticateAs("bidder-1", "AUCTION.BID");
        when(listMyBiddingUseCase.list("bidder-1", 0, 20)).thenReturn(List.of(
                new MyBiddingItem("auction-1", "product-1", "Watch", "ACTIVE", "LEADING", Instant.parse("2030-01-01T00:00:00Z"),
                        new BigDecimal("130.00"), new BigDecimal("130.00"), new BigDecimal("500.00"))));

        mockMvc.perform(get(URL).header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].auctionId").value("auction-1"))
                .andExpect(jsonPath("$.data[0].productName").value("Watch"))
                .andExpect(jsonPath("$.data[0].myStatus").value("LEADING"))
                .andExpect(jsonPath("$.data[0].myMaxAutoBid").value(500.00));
    }

    @Test
    void passesPageAndSizeThrough() throws Exception {
        authenticateAs("bidder-1", "AUCTION.BID");
        when(listMyBiddingUseCase.list("bidder-1", 2, 5)).thenReturn(List.of());

        mockMvc.perform(get(URL).param("page", "2").param("size", "5").header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(listMyBiddingUseCase).list("bidder-1", 2, 5);
    }

    @Test
    void returns400_whenPagingIsInvalid() throws Exception {
        authenticateAs("bidder-1", "AUCTION.BID");
        when(listMyBiddingUseCase.list("bidder-1", 0, 500))
                .thenThrow(new ValidationException(List.of(new FieldError("size", "must be between 1 and 100"))));

        mockMvc.perform(get(URL).param("size", "500").header("Authorization", "Bearer good-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
