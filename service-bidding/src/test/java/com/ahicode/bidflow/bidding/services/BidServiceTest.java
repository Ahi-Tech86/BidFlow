package com.ahicode.bidflow.bidding.services;

import com.ahicode.bidflow.bidding.dtos.BidPlacedEvent;
import com.ahicode.bidflow.bidding.dtos.BidRequest;
import com.ahicode.bidflow.bidding.dtos.BidResponse;
import com.ahicode.bidflow.bidding.entities.BidEntity;
import com.ahicode.bidflow.bidding.entities.LotEntity;
import com.ahicode.bidflow.bidding.enums.LotStatus;
import com.ahicode.bidflow.bidding.exceptions.BidTooLowException;
import com.ahicode.bidflow.bidding.exceptions.LotNotActiveException;
import com.ahicode.bidflow.bidding.exceptions.LotNotFoundException;
import com.ahicode.bidflow.bidding.exceptions.OwnerCannotBidException;
import com.ahicode.bidflow.bidding.mappers.BidMapper;
import com.ahicode.bidflow.bidding.mappers.LotMapper;
import com.ahicode.bidflow.bidding.repositories.BidRepository;
import com.ahicode.bidflow.bidding.repositories.LotRepository;
import com.ahicode.bidflow.common.enums.UserStatus;
import com.ahicode.bidflow.common.exceptions.UserNotAllowedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class BidServiceTest {

    private static final String LOT_INFO_PREFIX_KEY = "lots:";
    private static final String BID_PREFIX_KEY = "lot:bids:";

    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ObjectMapper redisObjectMapper;
    @Mock private LotRepository lotRepository;
    @Mock private BidRepository bidRepository;
    @Mock private LotMapper lotMapper;
    @Mock private BidMapper bidMapper;

    private BidServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BidServiceImpl(
                eventPublisher,
                stringRedisTemplate,
                redisObjectMapper,
                lotRepository,
                bidRepository,
                bidMapper
        );
    }

    @Nested
    class PlaceBid {
        private final ZonedDateTime now = ZonedDateTime.now();

        @Test
        void shouldReturnResponseAndPlaceBidSuccessfully() {
            BidRequest request = new BidRequest(BigDecimal.valueOf(100_000.00));
            UUID userId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();
            UserStatus userStatus = UserStatus.ACTIVE;

            LotEntity storedLot = new LotEntity();
            storedLot.setId(lotId);
            storedLot.setOwnerId(UUID.randomUUID());
            storedLot.setStatus(LotStatus.ACTIVE);
            storedLot.setStartPrice(BigDecimal.valueOf(50_000.00));
            storedLot.setMinBidStep(BigDecimal.valueOf(1_000.00));
            storedLot.setCurrentMaxBid(BigDecimal.valueOf(51_000.00));
            storedLot.setHighestBidderId(UUID.randomUUID());

            BidEntity bidEntity = new BidEntity();
            bidEntity.setBidValue(request.bidValue());

            UUID bidId = UUID.randomUUID();
            BidEntity savedBidEntity = new BidEntity();
            savedBidEntity.setId(bidId);
            savedBidEntity.setLotId(lotId);
            savedBidEntity.setUserId(userId);
            savedBidEntity.setBidValue(request.bidValue());

            BidResponse bidResponse = new BidResponse(
                    bidId, lotId, userId, request.bidValue(), now.minusMinutes(1)
            );

            given(lotRepository.findByIdWithLock(eq(lotId))).willReturn(Optional.of(storedLot));
            given(bidMapper.toEntity(request)).willReturn(bidEntity);
            given(bidRepository.save(eq(bidEntity))).willReturn(savedBidEntity);
            given(bidMapper.toResponse(eq(savedBidEntity))).willReturn(bidResponse);

            BidResponse actualResponse = service.placeBid(request, lotId, userId, userStatus);

            assertThat(actualResponse)
                    .isNotNull()
                    .extracting(BidResponse::id, BidResponse::lotId, BidResponse::userId, BidResponse::bidValue, BidResponse::createdAt)
                    .containsExactly(bidId, lotId, userId, request.bidValue(), now.minusMinutes(1));

            verify(lotRepository).findByIdWithLock(any(UUID.class));
            verify(eventPublisher).publishEvent(any(BidPlacedEvent.class));

            ArgumentCaptor<LotEntity> lotCaptor = ArgumentCaptor.forClass(LotEntity.class);
            verify(lotRepository).save(lotCaptor.capture());
            LotEntity capturedLot = lotCaptor.getValue();

            assertThat(capturedLot.getCurrentMaxBid()).isGreaterThan(BigDecimal.valueOf(51_000.00));
            assertThat(capturedLot.getCurrentMaxBid()).isEqualByComparingTo(request.bidValue());
            assertThat(capturedLot.getHighestBidderId()).isEqualTo(userId);

            ArgumentCaptor<BidEntity> bidCaptor = ArgumentCaptor.forClass(BidEntity.class);
            verify(bidRepository).save(bidCaptor.capture());
            BidEntity capturedBid = bidCaptor.getValue();

            assertThat(capturedBid.getBidValue()).isEqualByComparingTo(request.bidValue());
            assertThat(capturedBid.getUserId()).isEqualTo(userId);
        }

        @ParameterizedTest
        @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "BLOCKED"})
        void shouldThrowUserNotAllowedException_WhenUserNotAllowedMakeBid_BecauseAccountIsNotActive(UserStatus userStatus) {
            BidRequest request = new BidRequest(BigDecimal.valueOf(50_000.00));
            UUID userId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();

            assertThatThrownBy(() -> service.placeBid(request, lotId, userId, userStatus))
                    .isInstanceOf(UserNotAllowedException.class)
                    .hasMessageContaining("Your account has been suspended or blocked, please contact support");

            verifyNoInteractions(lotRepository, lotMapper, bidRepository, bidMapper);
        }

        @Test
        void shouldThrowLotNotFoundException_WhenLotDoesNotExistsById() {
            BidRequest request = new BidRequest(BigDecimal.valueOf(50_000.00));
            UUID userId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();

            given(lotRepository.findByIdWithLock(eq(lotId))).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.placeBid(request, lotId, userId, UserStatus.ACTIVE))
                    .isInstanceOf(LotNotFoundException.class)
                    .hasMessageContaining("Lot with id " + lotId + " not found.");

            verify(lotRepository).findByIdWithLock(any(UUID.class));
            verifyNoMoreInteractions(lotRepository);
            verifyNoInteractions(lotMapper, bidRepository, bidMapper);
        }

        @Test
        void shouldThrowOwnerCannotBidException_WhenOwnerOfLotTryingMakeBidOnOwnLot() {
            BidRequest request = new BidRequest(BigDecimal.valueOf(100_000.00));
            UUID userId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();

            LotEntity storedLot = new LotEntity();
            storedLot.setId(lotId);
            storedLot.setOwnerId(userId);
            storedLot.setStatus(LotStatus.ACTIVE);
            storedLot.setStartPrice(BigDecimal.valueOf(50_000.00));
            storedLot.setMinBidStep(BigDecimal.valueOf(1_000.00));

            given(lotRepository.findByIdWithLock(eq(lotId))).willReturn(Optional.of(storedLot));

            assertThatThrownBy(() -> service.placeBid(request, lotId, userId, UserStatus.ACTIVE))
                    .isInstanceOf(OwnerCannotBidException.class)
                    .hasMessageContaining("Owner cannot bid on their own lot");

            verify(lotRepository).findByIdWithLock(any(UUID.class));
            verifyNoMoreInteractions(lotRepository);
            verifyNoInteractions(lotMapper, bidRepository, bidMapper);
        }

        @ParameterizedTest
        @EnumSource(value = LotStatus.class, names = {"CANCELLED", "FINISHED"})
        void shouldThrowLotNotActiveException_WhenLotIsNotActive(LotStatus lotStatus) {
            BidRequest request = new BidRequest(BigDecimal.valueOf(100_000.00));
            UUID userId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();

            LotEntity storedLot = new LotEntity();
            storedLot.setId(lotId);
            storedLot.setOwnerId(UUID.randomUUID());
            storedLot.setStatus(lotStatus);
            storedLot.setStartPrice(BigDecimal.valueOf(50_000.00));
            storedLot.setMinBidStep(BigDecimal.valueOf(1_000.00));

            given(lotRepository.findByIdWithLock(eq(lotId))).willReturn(Optional.of(storedLot));

            assertThatThrownBy(() -> service.placeBid(request, lotId, userId, UserStatus.ACTIVE))
                    .isInstanceOf(LotNotActiveException.class)
                    .hasMessageContaining("Lot " + lotId + " is not active. Current status: " + lotStatus);

            verify(lotRepository).findByIdWithLock(any(UUID.class));
            verifyNoMoreInteractions(lotRepository);
            verifyNoInteractions(lotMapper, bidRepository, bidMapper);
        }

        @ParameterizedTest
        @ValueSource(doubles = {49_999.99, 50_000.00})
        void shouldThrowBidTooLowException_WhenBidIsLessOrEqualsToLotStartPrice(double bidValue) {
            BidRequest request = new BidRequest(BigDecimal.valueOf(bidValue));
            UUID userId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();

            LotEntity storedLot = new LotEntity();
            storedLot.setId(lotId);
            storedLot.setOwnerId(UUID.randomUUID());
            storedLot.setStatus(LotStatus.ACTIVE);
            storedLot.setStartPrice(BigDecimal.valueOf(50_000.00));
            storedLot.setMinBidStep(BigDecimal.valueOf(1_000.00));

            given(lotRepository.findByIdWithLock(eq(lotId))).willReturn(Optional.of(storedLot));

            assertThatThrownBy(() -> service.placeBid(request, lotId, userId, UserStatus.ACTIVE))
                    .isInstanceOf(BidTooLowException.class)
                    .hasMessageContaining("Bid must be greater than start price");

            verify(lotRepository).findByIdWithLock(any(UUID.class));
            verifyNoMoreInteractions(lotRepository);
            verifyNoInteractions(lotMapper, bidRepository, bidMapper);
        }

        @Test
        void shouldThrowBidTooLowException_WhenBidIsLessThanCurrentMaxBidPlusMinBidStep() {
            BidRequest request = new BidRequest(BigDecimal.valueOf(90_999.99));
            UUID userId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();
            UserStatus userStatus = UserStatus.ACTIVE;

            LotEntity storedLot = new LotEntity();
            storedLot.setId(lotId);
            storedLot.setOwnerId(UUID.randomUUID());
            storedLot.setStatus(LotStatus.ACTIVE);
            storedLot.setStartPrice(BigDecimal.valueOf(50_000.00));
            storedLot.setMinBidStep(BigDecimal.valueOf(1_000.00));
            storedLot.setCurrentMaxBid(BigDecimal.valueOf(90_000.00));

            given(lotRepository.findByIdWithLock(eq(lotId))).willReturn(Optional.of(storedLot));

            assertThatThrownBy(() -> service.placeBid(request, lotId, userId, userStatus))
                    .isInstanceOf(BidTooLowException.class)
                    .hasMessageContaining("Bid increment must be at least " + storedLot.getMinBidStep());

            verify(lotRepository).findByIdWithLock(any(UUID.class));
            verifyNoMoreInteractions(lotRepository, bidRepository);
            verifyNoInteractions(lotMapper, bidMapper);
        }
    }
}
