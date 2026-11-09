package com.nexus.auction.api;

import com.nexus.auction.application.usecase.ListMySellingUseCase;
import com.nexus.auction.application.usecase.MySellingItem;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MySellingController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class,
        PrivilegeAuthorizationAspect.class})
class MySellingControllerTest {

    private static final String URL = "/api/v1/auctions/mine/selling";

    @Autowired private MockMvc mockMvc;

    @MockBean private ListMySellingUseCase listMySellingUseCase;
    @MockBean private JwtTokenProvider jwtTokenProvider;

    private void authenticateAs(String subject, String... privileges) {
        when(jwtTokenProvider.isValid("good-token")).thenReturn(true);
        Claims claims = Jwts.claims().subject(subject).add("privileges", List.of(privileges)).build();
        when(jwtTokenProvider.parseClaims("good-token")).thenReturn(claims);
    }

    @Test
    void returns401WithoutToken() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verify(listMySellingUseCase, never()).list(anyString(), isNull(), anyInt(), anyInt());
    }

    @Test
    void returns403_forSomeoneWhoCannotCreateAuctions() throws Exception {
        authenticateAs("buyer-1", "AUCTION.BID");

        mockMvc.perform(get(URL).header("Authorization", "Bearer good-token")).andExpect(status().isForbidden());
        verify(listMySellingUseCase, never()).list(anyString(), isNull(), anyInt(), anyInt());
    }

    @Test
    void returnsTheListOfTheSellerInTheToken_withDefaultPaging() throws Exception {
        authenticateAs("seller-1", "AUCTION.CREATE");
        when(listMySellingUseCase.list("seller-1", null, 0, 20)).thenReturn(List.of(
                new MySellingItem("auction-1", "product-1", "Watch", "ACTIVE", new BigDecimal("100.00"),
                        new BigDecimal("10.00"), new BigDecimal("130.00"), Instant.parse("2030-01-01T00:00:00Z"),
                        Instant.parse("2030-01-02T00:00:00Z"), 7, 3, null, null, null, null)));

        mockMvc.perform(get(URL).header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].auctionId").value("auction-1"))
                .andExpect(jsonPath("$.data[0].productName").value("Watch"))
                .andExpect(jsonPath("$.data[0].bidCount").value(7))
                .andExpect(jsonPath("$.data[0].bidderCount").value(3));
    }

    @Test
    void passesStatusPageAndSizeThrough() throws Exception {
        authenticateAs("seller-1", "AUCTION.CREATE");
        when(listMySellingUseCase.list("seller-1", "ENDED", 2, 5)).thenReturn(List.of());

        mockMvc.perform(get(URL).param("status", "ENDED").param("page", "2").param("size", "5")
                        .header("Authorization", "Bearer good-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(listMySellingUseCase).list("seller-1", "ENDED", 2, 5);
    }

    @Test
    void returns400_whenTheStatusOrPagingIsInvalid() throws Exception {
        authenticateAs("seller-1", "AUCTION.CREATE");
        when(listMySellingUseCase.list("seller-1", "BOGUS", 0, 20))
                .thenThrow(new ValidationException(List.of(new FieldError("status", "Status must be one of [...]"))));

        mockMvc.perform(get(URL).param("status", "BOGUS").header("Authorization", "Bearer good-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
