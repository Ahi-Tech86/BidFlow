package com.ahicode.bidflow.bidding.services;

import com.ahicode.bidflow.bidding.dtos.*;
import com.ahicode.bidflow.bidding.entities.BidEntity;
import com.ahicode.bidflow.bidding.entities.LotEntity;
import com.ahicode.bidflow.bidding.enums.LotStatus;
import com.ahicode.bidflow.bidding.exceptions.*;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
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

    @Nested
    class GetLotsByStatus {
        @Test
        void shouldReturnMappedPage_WhenStatusIsProvided() {
            LotStatus requestedStatus = LotStatus.FINISHED;
            Pageable pageable = PageRequest.of(0, 10);

            LotEntity mockEntity = mock(LotEntity.class);
            Page<LotEntity> entityPage = new PageImpl<>(List.of(mockEntity), pageable, 1);

            LotListItemResponse mockResponse = mock(LotListItemResponse.class);

            given(lotRepository.findByStatus(eq(requestedStatus), eq(pageable))).willReturn(entityPage);
            given(lotMapper.toListItemResponse(eq(mockEntity))).willReturn(mockResponse);

            Page<LotListItemResponse> result = service.getLotsByStatus(requestedStatus, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst()).isEqualTo(mockResponse);
            assertThat(result.getTotalElements()).isEqualTo(1);

            verify(lotRepository).findByStatus(requestedStatus, pageable);
            verify(lotMapper).toListItemResponse(mockEntity);
        }

        @Test
        void shouldReturnMappedPageWithActiveStatus_WhenStatusIsNull() {
            Pageable pageable = PageRequest.of(0, 10);

            LotEntity mockEntity = mock(LotEntity.class);
            Page<LotEntity> entityPage = new PageImpl<>(List.of(mockEntity), pageable, 1);

            LotListItemResponse mockResponse = mock(LotListItemResponse.class);

            given(lotRepository.findByStatus(eq(LotStatus.ACTIVE), eq(pageable))).willReturn(entityPage);
            given(lotMapper.toListItemResponse(eq(mockEntity))).willReturn(mockResponse);

            Page<LotListItemResponse> result = service.getLotsByStatus(null, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst()).isEqualTo(mockResponse);
            assertThat(result.getTotalElements()).isEqualTo(1);

            verify(lotRepository).findByStatus(LotStatus.ACTIVE, pageable);
            verify(lotMapper).toListItemResponse(mockEntity);
        }

        @Test
        void shouldReturnEmptyPage_WhenNoLotsFound() {
            LotStatus requestedStatus = LotStatus.FINISHED;
            Pageable pageable = PageRequest.of(0, 10);

            Page<LotEntity> emptyEntityPage = Page.empty(pageable);

            given(lotRepository.findByStatus(eq(requestedStatus), eq(pageable))).willReturn(emptyEntityPage);

            Page<LotListItemResponse> result = service.getLotsByStatus(requestedStatus, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();

            verify(lotRepository).findByStatus(requestedStatus, pageable);
            verify(lotMapper, never()).toListItemResponse(any());
        }
    }

    @Nested
    class GetLotDetails {
        @Test
        void shouldReturnLotResponse() {
            ZonedDateTime now = ZonedDateTime.now();
            UUID lotId = UUID.randomUUID();
            LotEntity lotEntity = new LotEntity();
            lotEntity.setId(lotId);
            LotResponse expectedResponse = new LotResponse(
                    lotId,
                    UUID.randomUUID(),
                    "Some name",
                    "Some description",
                    BigDecimal.valueOf(100.00),
                    BigDecimal.valueOf(0.50),
                    LotStatus.ACTIVE,
                    now.plusDays(14),
                    now,
                    now
            );

            given(lotRepository.findById(eq(lotId))).willReturn(Optional.of(lotEntity));
            given(lotMapper.toResponse(eq(lotEntity))).willReturn(expectedResponse);

            LotResponse result = service.getLotDetails(lotId);

            assertThat(result)
                    .isNotNull()
                    .extracting(LotResponse::lotId, LotResponse::name, LotResponse::startPrice)
                    .containsExactly(lotId, "Some name", BigDecimal.valueOf(100.00));

            verify(lotRepository).findById(lotId);
            verify(lotMapper).toResponse(any(LotEntity.class));
        }

        @Test
        void shouldThrowException_WhenLotNotFoundByRequestedId() {
            UUID lotId = UUID.randomUUID();

            given(lotRepository.findById(eq(lotId))).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getLotDetails(lotId))
                    .isInstanceOf(LotNotFoundException.class)
                    .hasMessageContaining("Lot with id " + lotId + " not found.");
        }
    }

    @Nested
    class CancelAuctionLot {
        @Test
        void shouldReturnResponseSuccessfully() {
            ZonedDateTime now = ZonedDateTime.now();
            ZonedDateTime finishedAt = now.plusDays(10);
            ZonedDateTime createdAt = now.minusDays(10);
            UUID lotId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UserStatus userStatus = UserStatus.ACTIVE;

            LotEntity lot = new LotEntity();
            lot.setId(lotId);
            lot.setOwnerId(userId);
            lot.setStatus(LotStatus.ACTIVE);

            LotEntity savedLot = new LotEntity();
            savedLot.setStatus(LotStatus.CANCELLED);
            savedLot.setCreatedAt(createdAt);

            LotResponse expectedResponse = new LotResponse(
                    lotId, userId, "Name", "Description", BigDecimal.valueOf(100.00),
                    BigDecimal.valueOf(5.00), LotStatus.CANCELLED, finishedAt, createdAt, now
            );

            given(lotRepository.findById(any(UUID.class))).willReturn(Optional.of(lot));
            given(lotRepository.save(any(LotEntity.class))).willReturn(savedLot);
            given(lotMapper.toResponse(any(LotEntity.class))).willReturn(expectedResponse);

            LotResponse actualResponse = service.cancelAuctionLot(lotId, userId, userStatus);

            assertThat(actualResponse)
                    .isNotNull()
                    .extracting(LotResponse::lotId, LotResponse::name, LotResponse::status)
                    .containsExactly(lotId, "Name", LotStatus.CANCELLED);

            assertThat(actualResponse.updatedAt()).isAfter(actualResponse.createdAt());

            verify(lotRepository).findById(any(UUID.class));
            verify(lotMapper).toResponse(any(LotEntity.class));

            ArgumentCaptor<LotEntity> lotCaptor = ArgumentCaptor.forClass(LotEntity.class);
            verify(lotRepository).save(lotCaptor.capture());
            assertThat(lotCaptor.getValue().getStatus()).isEqualTo(LotStatus.CANCELLED);
        }

        @ParameterizedTest
        @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "BLOCKED"})
        void shouldThrowException_WhenUserNotAllowedCancelLot_BecauseHisAccountNotActive(UserStatus userStatus) {
            assertThatThrownBy(() -> service.cancelAuctionLot(UUID.randomUUID(), UUID.randomUUID(), userStatus))
                    .isInstanceOf(UserNotAllowedException.class)
                    .hasMessageContaining("Your account has been suspended or blocked, please contact support");

            verifyNoInteractions(lotRepository, lotMapper);
        }

        @Test
        void shouldThrowException_WhenLotNotFoundByRequestedId() {
            UUID requestedLotId = UUID.randomUUID();

            given(lotRepository.findById(requestedLotId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelAuctionLot(requestedLotId, UUID.randomUUID(), UserStatus.ACTIVE))
                    .isInstanceOf(LotNotFoundException.class)
                    .hasMessageContaining("Lot with id " + requestedLotId + " not found.");

            verifyNoMoreInteractions(lotRepository);
            verifyNoInteractions(lotMapper);
        }

        @Test
        void shouldThrowException_WhenUserIsNotOwnerOfLot() {
            UUID requestedLotId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            LotEntity storedLot = new LotEntity();
            storedLot.setId(requestedLotId);
            storedLot.setOwnerId(UUID.randomUUID());

            given(lotRepository.findById(requestedLotId)).willReturn(Optional.of(storedLot));

            assertThatThrownBy(() -> service.cancelAuctionLot(requestedLotId, userId, UserStatus.ACTIVE))
                    .isInstanceOf(OwnerCannotCancelException.class)
                    .hasMessageContaining("Only the lot owner can cancel the auction.");

            verifyNoMoreInteractions(lotRepository);
            verifyNoInteractions(lotMapper);
        }

        @Test
        void shouldThrowException_WhenLotIsInactive() {
            UUID requestedLotId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            LotEntity storedLot = new LotEntity();
            storedLot.setId(requestedLotId);
            storedLot.setOwnerId(userId);
            storedLot.setStatus(LotStatus.FINISHED);

            given(lotRepository.findById(requestedLotId)).willReturn(Optional.of(storedLot));

            assertThatThrownBy(() -> service.cancelAuctionLot(requestedLotId, userId, UserStatus.ACTIVE))
                    .isInstanceOf(LotNotActiveException.class)
                    .hasMessageContaining("Lot " + requestedLotId + " is not active. " +
                            "Current status: " + storedLot.getStatus());

            verifyNoMoreInteractions(lotRepository);
            verifyNoInteractions(lotMapper);
        }
    }
}
