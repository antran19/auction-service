package com.nexus.auction.api;

import com.nexus.auction.api.mapper.AuctionApiMapperImpl;
import com.nexus.auction.application.usecase.*;
import com.nexus.auction.infrastructure.config.SecurityConfig;
import com.nexus.common.core.exception.ForbiddenException;
import com.nexus.common.security.JwtAuthenticationFilter;
import com.nexus.common.security.JwtTokenProvider;
import com.nexus.common.security.PrivilegeAuthorizationAspect;
import com.nexus.common.web.GlobalExceptionHandler;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuctionController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({GlobalExceptionHandler.class, AuctionApiMapperImpl.class, SecurityConfig.class,
        JwtAuthenticationFilter.class, PrivilegeAuthorizationAspect.class})
class AuctionControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private CreateAuctionUseCase createAuctionUseCase;
    @MockBean private UpdateAuctionUseCase updateAuctionUseCase;
    @MockBean private CancelAuctionUseCase cancelAuctionUseCase;
    @MockBean private AdminCancelAuctionUseCase adminCancelAuctionUseCase;
    @MockBean private GetAuctionUseCase getAuctionUseCase;
    @MockBean private ListAuctionsUseCase listAuctionsUseCase;
    @MockBean private JwtTokenProvider jwtTokenProvider;

    private void authenticateAs(String subject, String... privileges) {
        when(jwtTokenProvider.isValid("good-token")).thenReturn(true);
        Claims claims = Jwts.claims().subject(subject).add("privileges", List.of(privileges)).build();
        when(jwtTokenProvider.parseClaims("good-token")).thenReturn(claims);
    }

    private AuctionResult sampleResult(String sellerId) {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        return new AuctionResult("auction-id", "product-1", sellerId, new BigDecimal("100.00"),
                new BigDecimal("10.00"), null, null, "PENDING", start, start.plus(2, ChronoUnit.HOURS), 0,
                null, null, null, null);
    }

    @Test
    void create_returns401WithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/auctions")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"productId":"product-1","startingPrice":100.00,"bidIncrement":10.00,
                                 "startTime":"2026-10-01T00:00:00Z","endTime":"2026-10-02T00:00:00Z"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_ignoresSpoofedSellerId_usesJwtSubjectInstead() throws Exception {
        authenticateAs("seller-id", "AUCTION.CREATE");
        when(createAuctionUseCase.create(any())).thenReturn(sampleResult("seller-id"));

        mockMvc.perform(post("/api/v1/auctions")
                        .header("Authorization", "Bearer good-token")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"productId":"product-1","sellerId":"attacker-id","startingPrice":100.00,
                                 "bidIncrement":10.00,"startTime":"2026-10-01T00:00:00Z","endTime":"2026-10-02T00:00:00Z"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sellerId").value("seller-id"));

        verify(createAuctionUseCase).create(argThat(
                (CreateAuctionCommand cmd) -> cmd.sellerId().equals("seller-id")));
    }

    @Test
    void cancel_returns403WhenUseCaseRejectsNonOwner() throws Exception {
        authenticateAs("other-seller", "AUCTION.CANCEL");
        when(cancelAuctionUseCase.cancel("a-id", "other-seller"))
                .thenThrow(new ForbiddenException("AUCTION_NOT_OWNED", "not yours"));

        mockMvc.perform(delete("/api/v1/auctions/a-id").header("Authorization", "Bearer good-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUCTION_NOT_OWNED"));
    }

    @Test
    void adminCancel_returns403WithoutAdminCancelPrivilege() throws Exception {
        authenticateAs("seller-id", "AUCTION.CANCEL");

        mockMvc.perform(post("/api/v1/auctions/a-id/admin-cancel").header("Authorization", "Bearer good-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(adminCancelAuctionUseCase);
    }

    @Test
    void get_isPublicAndReturns200WithoutAToken() throws Exception {
        when(getAuctionUseCase.get("a-id")).thenReturn(sampleResult("seller-id"));

        mockMvc.perform(get("/api/v1/auctions/a-id"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("auction-id"));
    }

    @Test
    void list_isPublicAndBindsFilters() throws Exception {
        when(listAuctionsUseCase.list(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/auctions")
                        .param("status", "ACTIVE").param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<AuctionSearchQuery> captor = org.mockito.ArgumentCaptor.forClass(AuctionSearchQuery.class);
        verify(listAuctionsUseCase).list(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().status()).isEqualTo("ACTIVE");
        org.assertj.core.api.Assertions.assertThat(captor.getValue().page()).isEqualTo(1);
    }
}
