package com.ahicode.bidflow.bidding.controllers;

import com.ahicode.bidflow.bidding.dtos.*;
import com.ahicode.bidflow.bidding.enums.LotStatus;
import com.ahicode.bidflow.bidding.exceptions.*;
import com.ahicode.bidflow.bidding.services.LotService;
import com.ahicode.bidflow.common.enums.UserStatus;
import com.ahicode.bidflow.common.exceptions.UserNotAllowedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LotController.class)
class LotControllerTest {

    @Autowired private ObjectMapper objectMapper;
    @Autowired private MockMvc mockMvc;

    @MockitoBean private LotService lotService;

    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID LOT_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final ZonedDateTime now = ZonedDateTime.now();

    @Nested
    class CreateLot {
        @Test
        void shouldReturnCreated_WhenLotPlacedSuccessfully() throws Exception {
            CreateLotRequest request = new CreateLotRequest(
                    "Vintage Watch",
                    "Description",
                    BigDecimal.valueOf(1000.00),
                    BigDecimal.valueOf(10.00),
                    now.plusDays(10)
            );
            LotResponse expectedResponse = createMockLotResponse();

            given(lotService.createLot(any(CreateLotRequest.class), any(UUID.class), any(UserStatus.class)))
                    .willReturn(expectedResponse);

            mockMvc.perform(post("/api/v1/lots")
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Status", ACTIVE_STATUS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.lotId").value(LOT_ID.toString()))
                    .andExpect(jsonPath("$.name").value("Vintage Watch"));
        }

        @Test
        void shouldReturnBadRequest_WhenValidationFails() throws Exception {
            CreateLotRequest invalidRequest = new CreateLotRequest(
                    "Vintage Watch",
                    "Description",
                    BigDecimal.valueOf(-1000.00),
                    BigDecimal.valueOf(10.00),
                    now.plusDays(10)
            );

            mockMvc.perform(post("/api/v1/lots")
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Status", ACTIVE_STATUS)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturnForbidden_WhenUserAccountIsNotActive() throws Exception {
            CreateLotRequest request = new CreateLotRequest(
                    "Vintage Watch",
                    "Description",
                    BigDecimal.valueOf(1000.00),
                    BigDecimal.valueOf(10.00),
                    now.plusDays(10)
            );
            given(lotService.createLot(any(CreateLotRequest.class), any(), any()))
                    .willThrow(UserNotAllowedException.class);

            mockMvc.perform(post("/api/v1/lots")
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Status", "BLOCKED")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class MakeBid {

        @Test
        void shouldReturnCreated_WhenBidPlacedSuccessfully() throws Exception {
            BidRequest request = new BidRequest(new BigDecimal("150.00"));
            BidResponse expectedResponse = new BidResponse(
                    UUID.randomUUID(), LOT_ID, USER_ID, new BigDecimal("150.00"), ZonedDateTime.now()
            );

            given(lotService.makeBid(any(BidRequest.class), eq(LOT_ID), any(UUID.class), any(UserStatus.class)))
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
            given(lotService.makeBid(any(), eq(LOT_ID), any(), any()))
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
            given(lotService.makeBid(any(), eq(LOT_ID), any(), any()))
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
            given(lotService.makeBid(any(), eq(LOT_ID), any(), any()))
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
    class GetLotsByStatus {
        @Test
        void shouldReturnPaginatedLots_WhenNoParamsProvided() throws Exception {
            PageRequest expectedPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<LotListItemResponse> mockPage = new PageImpl<>(List.of(createMockListItem()), expectedPageable, 1);
            PageResponse<LotListItemResponse> mockPageResponse = PageResponse.from(mockPage);

            given(lotService.getLotsByStatus(null, expectedPageable)).willReturn(mockPageResponse);

            mockMvc.perform(get("/api/v1/lots")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void shouldReturnPaginatedLots_WhenStatusAndCustomPaginationProvided() throws Exception {
            PageRequest expectedPageable = PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<LotListItemResponse> mockPage = new PageImpl<>(List.of(), expectedPageable, 0);
            PageResponse<LotListItemResponse> mockPageResponse = PageResponse.from(mockPage);

            given(lotService.getLotsByStatus(LotStatus.CANCELLED, expectedPageable)).willReturn(mockPageResponse);

            mockMvc.perform(get("/api/v1/lots")
                            .param("lotStatus", "CANCELLED")
                            .param("page", "1")
                            .param("size", "10")
                            .param("sort", "createdAt,desc")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Nested
    class GetLotDetails {
        @Test
        void shouldReturnLotDetails_WhenLotExists() throws Exception {
            LotResponse mockResponse = createMockLotResponse();

            given(lotService.getLotDetails(LOT_ID)).willReturn(mockResponse);

            mockMvc.perform(get("/api/v1/lots/{lotId}", LOT_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lotId").value(LOT_ID.toString()))
                    .andExpect(jsonPath("$.ownerId").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.name").value(mockResponse.name()));
        }

        @Test
        void shouldReturnNotFound_WhenLotDoesNotExist() throws Exception {
            given(lotService.getLotDetails(LOT_ID)).willThrow(new LotNotFoundException(LOT_ID));

            mockMvc.perform(get("/api/v1/lots/{lotId}", LOT_ID)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class CancelAuctionLot {
        @Test
        void shouldCancelLot_WhenRequestIsValid() throws Exception {
            LotResponse mockResponse = createMockLotResponse();
            given(lotService.cancelAuctionLot(LOT_ID, USER_ID, UserStatus.ACTIVE)).willReturn(mockResponse);

            mockMvc.perform(patch("/api/v1/lots/{lotId}/cancel", LOT_ID)
                            .header("X-User-Id", USER_ID.toString())
                            .header("X-User-Status", "ACTIVE")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        void shouldReturnForbidden_WhenUserStatusIsNotActive() throws Exception {
            given(lotService.cancelAuctionLot(LOT_ID, USER_ID, UserStatus.BLOCKED))
                    .willThrow(new UserNotAllowedException());

            mockMvc.perform(patch("/api/v1/lots/{lotId}/cancel", LOT_ID)
                            .header("X-User-Id", USER_ID.toString())
                            .header("X-User-Status", "BLOCKED")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturnForbidden_WhenUserIsNotOwner() throws Exception {
            given(lotService.cancelAuctionLot(LOT_ID, USER_ID, UserStatus.ACTIVE))
                    .willThrow(new OwnerCannotCancelException());

            mockMvc.perform(patch("/api/v1/lots/{lotId}/cancel", LOT_ID)
                            .header("X-User-Id", USER_ID.toString())
                            .header("X-User-Status", "ACTIVE")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());
        }

        @Test
        void shouldReturnBadRequest_WhenLotIsNotActive() throws Exception {
            given(lotService.cancelAuctionLot(LOT_ID, USER_ID, UserStatus.ACTIVE))
                    .willThrow(new LotNotActiveException(LOT_ID, LotStatus.CANCELLED));

            mockMvc.perform(patch("/api/v1/lots/{lotId}/cancel", LOT_ID)
                            .header("X-User-Id", USER_ID.toString())
                            .header("X-User-Status", "ACTIVE")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturnNotFound_WhenLotDoesNotExist() throws Exception {
            given(lotService.cancelAuctionLot(LOT_ID, USER_ID, UserStatus.ACTIVE))
                    .willThrow(new LotNotFoundException(LOT_ID));

            mockMvc.perform(patch("/api/v1/lots/{lotId}/cancel", LOT_ID)
                            .header("X-User-Id", USER_ID.toString())
                            .header("X-User-Status", "ACTIVE")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
        }
    }

    private LotResponse createMockLotResponse() {
        return new LotResponse(
                LOT_ID,
                USER_ID,
                "Vintage Watch",
                "Description",
                BigDecimal.valueOf(1000.00),
                BigDecimal.valueOf(10.00),
                LotStatus.ACTIVE,
                now.plusDays(10),
                now.minusMinutes(1),
                now.minusMinutes(1)
        );
    }

    private LotListItemResponse createMockListItem() {
        return new LotListItemResponse(
                LOT_ID,
                USER_ID,
                "Vintage Watch",
                BigDecimal.valueOf(1000.00),
                LotStatus.ACTIVE,
                now.plusDays(10),
                now.minusMinutes(1)
        );
    }
}
