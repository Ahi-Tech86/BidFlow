package com.ahicode.bidflow.bidding.services;

import com.ahicode.bidflow.bidding.dtos.BidRequest;
import com.ahicode.bidflow.bidding.dtos.BidResponse;
import com.ahicode.bidflow.bidding.dtos.CreateLotRequest;
import com.ahicode.bidflow.bidding.dtos.LotResponse;
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

@ExtendWith(MockitoExtension.class)
public class LotServiceTest {

    @Mock private LotRepository lotRepository;
    @Mock private BidRepository bidRepository;
    @Mock private LotMapper lotMapper;
    @Mock private BidMapper bidMapper;

    private LotServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LotServiceImpl(lotRepository, bidRepository, lotMapper, bidMapper);
    }

    @Nested
    class CreateLot {
        private final ZonedDateTime now = ZonedDateTime.now();
        private final CreateLotRequest request = new CreateLotRequest(
                "Ferrari F40",
                "Classic sport car",
                BigDecimal.valueOf(200_000.00),
                BigDecimal.valueOf(1_000.00),
                now.plusDays(15)
        );

        @Test
        void shouldCreateLotAndReturnResponseSuccessfully() {
            UserStatus userStatus = UserStatus.ACTIVE;
            UUID userId = UUID.randomUUID();

            LotEntity entity = new LotEntity();
            UUID lotId = UUID.randomUUID();
            LotEntity savedEntity = new LotEntity();
            savedEntity.setId(lotId);

            LotResponse response = new LotResponse(
                    lotId, userId, request.name(), request.description(),
                    request.startPrice(), request.minBidStep(), LotStatus.ACTIVE,
                    request.finishedAt(), now.minusMinutes(1), now.minusMinutes(1)
            );

            given(lotMapper.toEntity(eq(request))).willReturn(entity);
            given(lotRepository.save(eq(entity))).willReturn(savedEntity);
            given(lotMapper.toResponse(eq(savedEntity))).willReturn(response);

            LotResponse actualResponse = service.createLot(request, userId, userStatus);

            assertThat(actualResponse)
                    .isNotNull()
                    .extracting(
                            LotResponse::lotId, LotResponse::ownerId, LotResponse::name, LotResponse::description,
                            LotResponse::startPrice, LotResponse::minBidStep, LotResponse::status, LotResponse::finishedAt,
                            LotResponse::createdAt, LotResponse::updatedAt
                    )
                    .containsExactly(
                            lotId, userId, request.name(), request.description(), request.startPrice(), request.minBidStep(),
                            LotStatus.ACTIVE, request.finishedAt(), response.createdAt(), response.updatedAt()
                    );

            ArgumentCaptor<LotEntity> lotCaptor = ArgumentCaptor.forClass(LotEntity.class);
            verify(lotRepository).save(lotCaptor.capture());
            assertThat(lotCaptor.getValue().getStatus()).isEqualTo(LotStatus.ACTIVE);
        }

        @ParameterizedTest
        @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "BLOCKED"})
        void shouldThrowException_WhenUserNotAllowedCreateLot(UserStatus userStatus) {
            assertThatThrownBy(() -> service.createLot(request, UUID.randomUUID(), userStatus))
                    .isInstanceOf(UserNotAllowedException.class)
                    .hasMessageContaining("Your account has been suspended or blocked, please contact support");

            verifyNoInteractions(lotRepository, lotMapper);
        }
    }

    @Nested
    class MakeBid {
        private final ZonedDateTime now = ZonedDateTime.now();

        @Test
        void shouldReturnResponseAndMakeBidSuccessfully() {
            BidRequest request = new BidRequest(BigDecimal.valueOf(100_000.00));
            UUID userId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();
            UserStatus userStatus = UserStatus.ACTIVE;

            LotEntity lot = new LotEntity();
            lot.setId(lotId);
            lot.setOwnerId(UUID.randomUUID());
            lot.setStatus(LotStatus.ACTIVE);
            lot.setStartPrice(BigDecimal.valueOf(50_000.00));
            lot.setMinBidStep(BigDecimal.valueOf(1_000.00));

            BigDecimal maxCurrentBidValue = BigDecimal.valueOf(90_000.00);

            BidEntity bidEntity = new BidEntity();
            UUID bidId = UUID.randomUUID();
            BidEntity savedBidEntity = new BidEntity();
            savedBidEntity.setId(bidId);
            savedBidEntity.setLotId(lotId);
            savedBidEntity.setUserId(userId);
            savedBidEntity.setBidValue(request.bidValue());

            BidResponse bidResponse = new BidResponse(
                    bidId, lotId, userId, request.bidValue(), now.minusMinutes(1)
            );

            given(lotRepository.findById(eq(lotId))).willReturn(Optional.of(lot));
            given(bidRepository.findMaxBidForLotByLotId(eq(lotId))).willReturn(maxCurrentBidValue);
            given(bidMapper.toEntity(request)).willReturn(bidEntity);
            given(bidRepository.save(eq(bidEntity))).willReturn(savedBidEntity);
            given(bidMapper.toResponse(eq(savedBidEntity))).willReturn(bidResponse);

            BidResponse actualResponse = service.makeBid(request, lotId, userId, userStatus);

            assertThat(actualResponse)
                    .isNotNull()
                    .extracting(BidResponse::id, BidResponse::lotId, BidResponse::userId, BidResponse::bidValue, BidResponse::createdAt)
                    .containsExactly(bidId, lotId, userId, request.bidValue(), now.minusMinutes(1));
        }

        @ParameterizedTest
        @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "BLOCKED"})
        void shouldThrowException_WhenUserNotAllowedMakeBid_BecauseAccountIsNotActive(UserStatus userStatus) {
            BidRequest request = new BidRequest(BigDecimal.valueOf(50_000.00));
            UUID userId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();

            assertThatThrownBy(() -> service.makeBid(request, lotId, userId, userStatus))
                    .isInstanceOf(UserNotAllowedException.class)
                    .hasMessageContaining("Your account has been suspended or blocked, please contact support");

            verifyNoInteractions(lotRepository, lotMapper, bidRepository, bidMapper);
        }

        @Test
        void shouldThrowException_WhenLotDoesNotExistsById() {
            BidRequest request = new BidRequest(BigDecimal.valueOf(50_000.00));
            UUID userId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();

            given(lotRepository.findById(eq(lotId))).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.makeBid(request, lotId, userId, UserStatus.ACTIVE))
                    .isInstanceOf(LotNotFoundException.class)
                    .hasMessageContaining("Lot with id " + lotId + " not found.");

            verify(lotRepository).findById(any(UUID.class));
            verifyNoMoreInteractions(lotRepository);
            verifyNoInteractions(lotMapper, bidRepository, bidMapper);
        }

        @Test
        void shouldThrowException_WhenOwnerOfLotTryingMakeBidOnOwnLot() {
            BidRequest request = new BidRequest(BigDecimal.valueOf(100_000.00));
            UUID userId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();

            LotEntity lot = new LotEntity();
            lot.setId(lotId);
            lot.setOwnerId(userId);
            lot.setStatus(LotStatus.ACTIVE);
            lot.setStartPrice(BigDecimal.valueOf(50_000.00));
            lot.setMinBidStep(BigDecimal.valueOf(1_000.00));

            given(lotRepository.findById(eq(lotId))).willReturn(Optional.of(lot));

            assertThatThrownBy(() -> service.makeBid(request, lotId, userId, UserStatus.ACTIVE))
                    .isInstanceOf(OwnerCannotBidException.class)
                    .hasMessageContaining("Owner cannot bid on their own lot");

            verify(lotRepository).findById(any(UUID.class));
            verifyNoMoreInteractions(lotRepository);
            verifyNoInteractions(lotMapper, bidRepository, bidMapper);
        }

        @ParameterizedTest
        @EnumSource(value = LotStatus.class, names = {"CANCELLED", "FINISHED"})
        void shouldThrowException_WhenLotIsNotActive(LotStatus lotStatus) {
            BidRequest request = new BidRequest(BigDecimal.valueOf(100_000.00));
            UUID userId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();

            LotEntity lot = new LotEntity();
            lot.setId(lotId);
            lot.setOwnerId(UUID.randomUUID());
            lot.setStatus(lotStatus);
            lot.setStartPrice(BigDecimal.valueOf(50_000.00));
            lot.setMinBidStep(BigDecimal.valueOf(1_000.00));

            given(lotRepository.findById(eq(lotId))).willReturn(Optional.of(lot));

            assertThatThrownBy(() -> service.makeBid(request, lotId, userId, UserStatus.ACTIVE))
                    .isInstanceOf(LotNotActiveException.class)
                    .hasMessageContaining("Lot " + lotId + " is not active. Current status: " + lotStatus);

            verify(lotRepository).findById(any(UUID.class));
            verifyNoMoreInteractions(lotRepository);
            verifyNoInteractions(lotMapper, bidRepository, bidMapper);
        }

        @ParameterizedTest
        @ValueSource(doubles = {49_999.99, 50_000.00})
        void shouldThrowException_WhenBidIsLessOrEqualsToLotStartPrice(double bidValue) {
            BidRequest request = new BidRequest(BigDecimal.valueOf(bidValue));
            UUID userId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();

            LotEntity lot = new LotEntity();
            lot.setId(lotId);
            lot.setOwnerId(UUID.randomUUID());
            lot.setStatus(LotStatus.ACTIVE);
            lot.setStartPrice(BigDecimal.valueOf(50_000.00));
            lot.setMinBidStep(BigDecimal.valueOf(1_000.00));

            given(lotRepository.findById(eq(lotId))).willReturn(Optional.of(lot));

            assertThatThrownBy(() -> service.makeBid(request, lotId, userId, UserStatus.ACTIVE))
                    .isInstanceOf(BidTooLowException.class)
                    .hasMessageContaining("Bid must be greater than start price");

            verify(lotRepository).findById(any(UUID.class));
            verifyNoMoreInteractions(lotRepository);
            verifyNoInteractions(lotMapper, bidRepository, bidMapper);
        }

        @Test
        void shouldThrowException_WhenBidIsLessThanCurrentMaxBidPlusMinBidStep() {
            BidRequest request = new BidRequest(BigDecimal.valueOf(90_999.99));
            UUID userId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();
            UserStatus userStatus = UserStatus.ACTIVE;

            LotEntity lot = new LotEntity();
            lot.setId(lotId);
            lot.setOwnerId(UUID.randomUUID());
            lot.setStatus(LotStatus.ACTIVE);
            lot.setStartPrice(BigDecimal.valueOf(50_000.00));
            lot.setMinBidStep(BigDecimal.valueOf(1_000.00));

            BigDecimal maxCurrentBidValue = BigDecimal.valueOf(90_000.00);

            given(lotRepository.findById(eq(lotId))).willReturn(Optional.of(lot));
            given(bidRepository.findMaxBidForLotByLotId(eq(lotId))).willReturn(maxCurrentBidValue);

            assertThatThrownBy(() -> service.makeBid(request, lotId, userId, userStatus))
                    .isInstanceOf(BidTooLowException.class)
                    .hasMessageContaining("Bid increment must be at least " + lot.getMinBidStep());

            verify(lotRepository).findById(any(UUID.class));
            verify(bidRepository).findMaxBidForLotByLotId(any(UUID.class));
            verifyNoMoreInteractions(lotRepository, bidRepository);
            verifyNoInteractions(lotMapper, bidMapper);
        }
    }
}
