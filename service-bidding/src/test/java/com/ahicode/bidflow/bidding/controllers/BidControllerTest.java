package com.ahicode.bidflow.bidding.controllers;

import com.ahicode.bidflow.bidding.dtos.BidHistoryResponse;
import com.ahicode.bidflow.bidding.dtos.BidRequest;
import com.ahicode.bidflow.bidding.dtos.BidResponse;
import com.ahicode.bidflow.bidding.dtos.PageResponse;
import com.ahicode.bidflow.bidding.exceptions.BidTooLowException;
import com.ahicode.bidflow.bidding.exceptions.LotNotFoundException;
import com.ahicode.bidflow.bidding.exceptions.OwnerCannotBidException;
import com.ahicode.bidflow.bidding.services.BidService;
import com.ahicode.bidflow.common.enums.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Nested
    class GetBidsHistory {
        @Test
        @DisplayName("should return 200 OK and paginated bids when lot exists")
        void shouldReturnOkAndPaginatedBids_WhenLotExists() throws Exception {
            BidHistoryResponse bid1 = new BidHistoryResponse(UUID.randomUUID(), USER_ID, new BigDecimal("150.00"), now);
            BidHistoryResponse bid2 = new BidHistoryResponse(UUID.randomUUID(), USER_ID, new BigDecimal("200.00"), now);
            Page<BidHistoryResponse> mockPage = new PageImpl<>(
                    List.of(bid1, bid2),
                    PageRequest.of(0, 10),
                    2
            );

            PageResponse<BidHistoryResponse> pageResponse = PageResponse.from(mockPage);

            given(bidService.getBidsHistory(eq(LOT_ID), any(Pageable.class))).willReturn(pageResponse);

            mockMvc.perform(get("/api/v1/lots/{lotId}/bids", LOT_ID)
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @DisplayName("should return 200 OK and empty page when no bids exist")
        void shouldReturnOkAndEmptyPage_WhenNoBidsExist() throws Exception {
            Page<BidHistoryResponse> mockPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
            PageResponse<BidHistoryResponse> emptyPageResponse = PageResponse.from(mockPage);

            given(bidService.getBidsHistory(eq(LOT_ID), any(Pageable.class))).willReturn(emptyPageResponse);

            mockMvc.perform(get("/api/v1/lots/{lotId}/bids", LOT_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("should return 400 Bad Request when lotId is not a valid UUID")
        void shouldReturnBadRequest_WhenLotIdIsInvalid() throws Exception {
            mockMvc.perform(get("/api/v1/lots/{lotId}/bids", "invalid-uuid-format"))
                    .andExpect(status().isBadRequest());
        }
    }
}
