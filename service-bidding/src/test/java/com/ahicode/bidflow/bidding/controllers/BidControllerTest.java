package com.ahicode.bidflow.bidding.controllers;

import com.ahicode.bidflow.bidding.dtos.BidRequest;
import com.ahicode.bidflow.bidding.dtos.BidResponse;
import com.ahicode.bidflow.bidding.exceptions.BidTooLowException;
import com.ahicode.bidflow.bidding.exceptions.LotNotFoundException;
import com.ahicode.bidflow.bidding.exceptions.OwnerCannotBidException;
import com.ahicode.bidflow.bidding.services.BidService;
import com.ahicode.bidflow.common.enums.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BidController.class)
public class BidControllerTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private MockMvc mockMvc;

    @MockitoBean private BidService bidService;

    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID LOT_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final ZonedDateTime now = ZonedDateTime.now();

    @Nested
    class PlaceBid {

        @Test
        void shouldReturnCreated_WhenBidPlacedSuccessfully() throws Exception {
            BidRequest request = new BidRequest(new BigDecimal("150.00"));
            BidResponse expectedResponse = new BidResponse(
                    UUID.randomUUID(), LOT_ID, USER_ID, new BigDecimal("150.00"), ZonedDateTime.now()
            );

            given(bidService.placeBid(any(BidRequest.class), eq(LOT_ID), any(UUID.class), any(UserStatus.class)))
                    .willReturn(expectedResponse);

            mockMvc.perform(post("/api/v1/lots/{lotId}/bids", LOT_ID)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Status", ACTIVE_STATUS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.lotId").value(LOT_ID.toString()))
                    .andExpect(jsonPath("$.bidValue").value("150.0"));
        }

        @Test
        void shouldReturnBadRequest_WhenBidValueIsNotPositive() throws Exception {
            BidRequest invalidRequest = new BidRequest(new BigDecimal("-10.00"));

            mockMvc.perform(post("/api/v1/lots/{lotId}/bids", LOT_ID)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Status", ACTIVE_STATUS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturnNotFound_WhenLotDoesNotExist() throws Exception {
            BidRequest request = new BidRequest(new BigDecimal("150.00"));
            given(bidService.placeBid(any(), eq(LOT_ID), any(), any()))
                    .willThrow(new LotNotFoundException(LOT_ID));

            mockMvc.perform(post("/api/v1/lots/{lotId}/bids", LOT_ID)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Status", ACTIVE_STATUS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturnForbidden_WhenOwnerTriesToBid() throws Exception {
            BidRequest request = new BidRequest(new BigDecimal("150.00"));
            given(bidService.placeBid(any(), eq(LOT_ID), any(), any()))
                    .willThrow(new OwnerCannotBidException());

            mockMvc.perform(post("/api/v1/lots/{lotId}/bids", LOT_ID)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Status", ACTIVE_STATUS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturnBadRequest_WhenBidIsTooLow() throws Exception {
            BidRequest request = new BidRequest(new BigDecimal("50.00"));
            given(bidService.placeBid(any(), eq(LOT_ID), any(), any()))
                    .willThrow(new BidTooLowException("Bid must be greater than start price"));

            mockMvc.perform(post("/api/v1/lots/{lotId}/bids", LOT_ID)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Status", ACTIVE_STATUS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }
}
