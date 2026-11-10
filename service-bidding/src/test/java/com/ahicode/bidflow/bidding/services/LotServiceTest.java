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
import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LotServiceTest {

    private static final String LOT_INFO_PREFIX_KEY = "lots:";
    private static final String BID_PREFIX_KEY = "lot:bids:";

    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ObjectMapper redisObjectMapper;
    @Mock private LotRepository lotRepository;
    @Mock private BidRepository bidRepository;
    @Mock private LotMapper lotMapper;
    @Mock private BidMapper bidMapper;

    private LotServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LotServiceImpl(
                stringRedisTemplate, redisObjectMapper, lotRepository, bidRepository, lotMapper, bidMapper
        );
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

        @BeforeEach
        void setUp() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        }

        @Test
        void shouldCreateLotAndReturnResponseSuccessfully() throws JsonProcessingException {
            UserStatus userStatus = UserStatus.ACTIVE;
            UUID userId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();

            LotEntity entityToSave = new LotEntity();
            entityToSave.setStartPrice(request.startPrice());

            LotEntity savedEntity = new LotEntity();
            savedEntity.setId(lotId);
            savedEntity.setOwnerId(userId);
            savedEntity.setCurrentMaxBid(request.startPrice());
            savedEntity.setStatus(LotStatus.ACTIVE);

            LotResponse response = makeLotResponse(lotId, userId);

            // Mocking mapper and repository
            given(lotMapper.toEntity(eq(request))).willReturn(entityToSave);
            given(lotRepository.save(eq(entityToSave))).willReturn(savedEntity);
            given(lotMapper.toResponse(eq(savedEntity))).willReturn(response);

            // Mocking Redis
            given(redisObjectMapper.writeValueAsString(eq(response))).willReturn("mockedLotJson");
            given(redisObjectMapper.writeValueAsString(any(CurrentBidInfo.class))).willReturn("mockedBidInfo");

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
            LotEntity capturedEntity = lotCaptor.getValue();

            assertThat(capturedEntity.getOwnerId()).isEqualTo(userId);
            assertThat(capturedEntity.getCurrentMaxBid()).isEqualByComparingTo(request.startPrice());
            assertThat(capturedEntity.getStatus()).isEqualTo(LotStatus.ACTIVE);

            String expectedLotKey = "lots:" + lotId;
            String expectedBidKey = "lot:bids:" + lotId;

            verify(valueOperations).set(eq(expectedLotKey), eq("mockedLotJson"), eq(1L), eq(TimeUnit.HOURS));
            verify(valueOperations).set(eq(expectedBidKey), eq("mockedBidInfo"), eq(30L), eq(TimeUnit.MINUTES));
        }

        @Test
        @MockitoSettings(strictness = Strictness.LENIENT)
        void shouldThrowException_WhenRequestedUserStatusIsNull() {
            assertThatThrownBy(() -> service.createLot(request, UUID.randomUUID(), null))
                    .isInstanceOf(UserNotAllowedException.class)
                    .hasMessageContaining("Your account has been suspended or blocked, please contact support");

            verifyNoInteractions(lotRepository, lotMapper, valueOperations);
        }

        @ParameterizedTest
        @EnumSource(value = UserStatus.class, names = {"SUSPENDED", "BLOCKED"})
        @MockitoSettings(strictness = Strictness.LENIENT)
        void shouldThrowException_WhenUserNotAllowedCreateLot(UserStatus userStatus) {
            assertThatThrownBy(() -> service.createLot(request, UUID.randomUUID(), userStatus))
                    .isInstanceOf(UserNotAllowedException.class)
                    .hasMessageContaining("Your account has been suspended or blocked, please contact support");

            verifyNoInteractions(lotRepository, lotMapper, valueOperations);
        }

        @Test
        @MockitoSettings(strictness = Strictness.LENIENT)
        void shouldThrowException_WhenLotInfoSerializationFails() throws JsonProcessingException {
            UUID lotId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            BigDecimal startPrice = request.startPrice();

            LotEntity entityToSave = new LotEntity();
            entityToSave.setStartPrice(startPrice);

            LotEntity savedEntity = new LotEntity();
            savedEntity.setId(lotId);
            savedEntity.setStartPrice(startPrice);

            LotResponse mockResponse = makeLotResponse(lotId, userId);

            given(lotMapper.toEntity(any(CreateLotRequest.class))).willReturn(entityToSave);
            given(lotRepository.save(any(LotEntity.class))).willReturn(savedEntity);

            given(lotMapper.toResponse(eq(savedEntity))).willReturn(mockResponse);

            given(redisObjectMapper.writeValueAsString(any(LotResponse.class)))
                    .willThrow(new JsonProcessingException("Serialization error") {});

            assertThatThrownBy(() -> service.createLot(request, UUID.randomUUID(), UserStatus.ACTIVE))
                    .isInstanceOf(CacheSerializationException.class)
                    .hasMessageContaining("Internal server error during cache serialization");

            verify(lotRepository).save(any(LotEntity.class));
            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @MockitoSettings(strictness = Strictness.LENIENT)
        void shouldThrowException_WhenBidInfoSerializationFails() throws JsonProcessingException {
            UUID lotId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            LotEntity savedEntity = new LotEntity();
            savedEntity.setId(UUID.randomUUID());

            given(lotMapper.toEntity(any(CreateLotRequest.class))).willReturn(new LotEntity());
            given(lotRepository.save(any(LotEntity.class))).willReturn(new LotEntity());
            given(lotMapper.toResponse(any(LotEntity.class))).willReturn(makeLotResponse(lotId, userId));

            given(redisObjectMapper.writeValueAsString(any(LotResponse.class))).willReturn("mockLotJson");
            given(redisObjectMapper.writeValueAsString(any(CurrentBidInfo.class)))
                    .willThrow(new JsonProcessingException("Bid serialization error") {});

            assertThatThrownBy(() -> service.createLot(request, userId, UserStatus.ACTIVE))
                    .isInstanceOf(CacheSerializationException.class)
                    .hasMessageContaining("Internal server error during cache serialization");

            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }

        private LotResponse makeLotResponse(UUID lotId, UUID userId) {
            return new LotResponse(
                    lotId, userId, request.name(), request.description(),
                    request.startPrice(), request.minBidStep(), LotStatus.ACTIVE,
                    request.finishedAt(), now.minusMinutes(1), now.minusMinutes(1)
            );
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

            PageResponse<LotListItemResponse> result = service.getLotsByStatus(requestedStatus, pageable);

            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().getFirst()).isEqualTo(mockResponse);
            assertThat(result.totalElements()).isEqualTo(1);

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

            PageResponse<LotListItemResponse> result = service.getLotsByStatus(null, pageable);

            assertThat(result).isNotNull();
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().getFirst()).isEqualTo(mockResponse);
            assertThat(result.totalElements()).isEqualTo(1);

            verify(lotRepository).findByStatus(LotStatus.ACTIVE, pageable);
            verify(lotMapper).toListItemResponse(mockEntity);
        }

        @Test
        void shouldReturnEmptyPage_WhenNoLotsFound() {
            LotStatus requestedStatus = LotStatus.FINISHED;
            Pageable pageable = PageRequest.of(0, 10);

            Page<LotEntity> emptyEntityPage = Page.empty(pageable);

            given(lotRepository.findByStatus(eq(requestedStatus), eq(pageable))).willReturn(emptyEntityPage);

            PageResponse<LotListItemResponse> result = service.getLotsByStatus(requestedStatus, pageable);

            assertThat(result).isNotNull();
            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();

            verify(lotRepository).findByStatus(requestedStatus, pageable);
            verify(lotMapper, never()).toListItemResponse(any());
        }
    }

    @Nested
    class GetLotDetails {
        private final ZonedDateTime now = ZonedDateTime.now();
        private final UUID lotId = UUID.randomUUID();
        private final String lotInfoKey = LOT_INFO_PREFIX_KEY + lotId;
        private final String bidInfoKey = BID_PREFIX_KEY + lotId;

        @BeforeEach
        void setUp() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        }

        @Test
        void shouldReturnLotDetailsResponseFromCache_WhenBothCacheEntriesExistAndAreValid() throws JsonProcessingException {
            LotResponse lotInfo = makeLotResponse(lotId, UUID.randomUUID());
            CurrentBidInfo bidInfo = new CurrentBidInfo(lotInfo.startPrice(), null);
            LotDetailsResponse expectedLotResponse = makeLotDetailsResponse(lotInfo, bidInfo);

            given(valueOperations.get(eq(lotInfoKey))).willReturn("mockLotInfo");
            given(valueOperations.get(eq(bidInfoKey))).willReturn("mockBidInfo");
            given(redisObjectMapper.readValue(eq("mockLotInfo"), eq(LotResponse.class))).willReturn(lotInfo);
            given(redisObjectMapper.readValue(eq("mockBidInfo"), eq(CurrentBidInfo.class))).willReturn(bidInfo);
            given(lotMapper.toDetailsResponse(eq(lotInfo), eq(bidInfo))).willReturn(expectedLotResponse);

            LotDetailsResponse actualResponse = service.getLotDetails(lotId);

            assertThat(actualResponse)
                    .isNotNull()
                    .extracting(
                            LotDetailsResponse::lotId, LotDetailsResponse::name, LotDetailsResponse::startPrice
                    )
                    .containsExactly(lotId, lotInfo.name(), lotInfo.startPrice());

            verifyNoInteractions(lotRepository);

            verify(valueOperations).get(eq(lotInfoKey));
            verify(valueOperations).get(eq(bidInfoKey));

            verify(redisObjectMapper).readValue(eq("mockLotInfo"), eq(LotResponse.class));
            verify(redisObjectMapper).readValue(eq("mockBidInfo"), eq(CurrentBidInfo.class));

            verifyNoMoreInteractions(valueOperations);
            verify(lotMapper).toDetailsResponse(any(LotResponse.class), any(CurrentBidInfo.class));
        }

        @Test
        void shouldFetchLotInfoFromDbAndPopulateCache_WhenLotInfoCacheIsEmptyButBidInfoExists() throws JsonProcessingException {
            LotEntity storedEntity = new LotEntity();
            storedEntity.setId(lotId);
            storedEntity.setCurrentMaxBid(BigDecimal.valueOf(500.00));
            storedEntity.setHighestBidderId(UUID.randomUUID());

            CurrentBidInfo bidInfo = new CurrentBidInfo(storedEntity.getCurrentMaxBid(), storedEntity.getHighestBidderId());
            LotResponse fetchedLotInfo = makeLotResponse(lotId, UUID.randomUUID());
            LotDetailsResponse expectedDetailsResponse = makeLotDetailsResponse(fetchedLotInfo, bidInfo);

            // Lots cache is empty
            given(valueOperations.get(lotInfoKey)).willReturn(null);
            // Bids cache is not empty
            given(valueOperations.get(bidInfoKey)).willReturn("validJsonBid");

            // Mocking bid cache deserialization
            given(redisObjectMapper.readValue("validJsonBid", CurrentBidInfo.class)).willReturn(bidInfo);

            // Mocking a database call
            given(lotRepository.findById(lotId)).willReturn(Optional.of(storedEntity));

            // Mocking mapping to LotResponse
            given(lotMapper.toResponse(storedEntity)).willReturn(fetchedLotInfo);

            // Mocking serialization new cache value
            given(redisObjectMapper.writeValueAsString(fetchedLotInfo)).willReturn("newJsonLot");

            // Mocking mapping to LotDetailsResponse
            given(lotMapper.toDetailsResponse(fetchedLotInfo, bidInfo)).willReturn(expectedDetailsResponse);

            LotDetailsResponse actualDetailsResponse = service.getLotDetails(lotId);

            assertThat(actualDetailsResponse).isEqualTo(expectedDetailsResponse);

            verify(lotRepository, times(1)).findById(any(UUID.class));
            verify(valueOperations).set(eq(lotInfoKey), eq("newJsonLot"), anyLong(), any(TimeUnit.class));
            verify(valueOperations, never()).set(eq(bidInfoKey), anyString(), anyLong(), any(TimeUnit.class));
            verify(lotMapper).toDetailsResponse(eq(fetchedLotInfo), eq(bidInfo));
        }

        @Test
        void shouldFetchBidInfoFromDbAndPopulateCache_WhenBidInfoCacheIsEmptyButLotInfoExists() throws JsonProcessingException {
            LotEntity storedEntity = new LotEntity();
            storedEntity.setId(lotId);
            storedEntity.setCurrentMaxBid(BigDecimal.valueOf(500.00));
            storedEntity.setHighestBidderId(UUID.randomUUID());

            CurrentBidInfo fetchedBidInfo = new CurrentBidInfo(storedEntity.getCurrentMaxBid(), storedEntity.getHighestBidderId());
            LotResponse lotInfo = makeLotResponse(lotId, UUID.randomUUID());
            LotDetailsResponse expectedDetailsResponse = makeLotDetailsResponse(lotInfo, fetchedBidInfo);

            // Lot cache is not empty
            given(valueOperations.get(lotInfoKey)).willReturn("validJsonLot");
            // Bid cache is empty
            given(valueOperations.get(bidInfoKey)).willReturn(null);

            // Mocking lot cache deserialization
            given(redisObjectMapper.readValue("validJsonLot", LotResponse.class)).willReturn(lotInfo);

            // Mocking a database call
            given(lotRepository.findById(lotId)).willReturn(Optional.of(storedEntity));

            // Mocking serialization new cache value
            given(redisObjectMapper.writeValueAsString(fetchedBidInfo)).willReturn("newJsonBid");

            // Mocking mapping to LotDetailsResponse
            given(lotMapper.toDetailsResponse(lotInfo, fetchedBidInfo)).willReturn(expectedDetailsResponse);

            LotDetailsResponse actualDetailsResponse = service.getLotDetails(lotId);

            assertThat(actualDetailsResponse).isEqualTo(expectedDetailsResponse);

            verify(lotRepository, times(1)).findById(any(UUID.class));
            verify(valueOperations).set(eq(bidInfoKey), eq("newJsonBid"), anyLong(), any(TimeUnit.class));
            verify(valueOperations, never()).set(eq(lotInfoKey), anyString(), anyLong(), any(TimeUnit.class));
            verify(lotMapper).toDetailsResponse(eq(lotInfo), eq(fetchedBidInfo));
        }

        @Test
        void shouldFetchAllDataFromDbAndPopulateBothCaches_WhenBothCacheEntriesAreEmpty() throws JsonProcessingException {
            LotEntity storedEntity = new LotEntity();
            storedEntity.setId(lotId);
            storedEntity.setCurrentMaxBid(BigDecimal.valueOf(500.00));
            storedEntity.setHighestBidderId(UUID.randomUUID());

            CurrentBidInfo fetchedBidInfo = new CurrentBidInfo(storedEntity.getCurrentMaxBid(), storedEntity.getHighestBidderId());
            LotResponse fetchedLotInfo = makeLotResponse(lotId, UUID.randomUUID());
            LotDetailsResponse expectedDetailsResponse = makeLotDetailsResponse(fetchedLotInfo, fetchedBidInfo);

            // Both cache is empty
            given(valueOperations.get(lotInfoKey)).willReturn(null);
            given(valueOperations.get(bidInfoKey)).willReturn(null);

            // Mocking a database call
            given(lotRepository.findById(lotId)).willReturn(Optional.of(storedEntity));

            // Mocking mapping to LotResponse
            given(lotMapper.toResponse(storedEntity)).willReturn(fetchedLotInfo);

            // Mocking serialization for new cache value
            given(redisObjectMapper.writeValueAsString(fetchedLotInfo)).willReturn("newJsonLot");
            given(redisObjectMapper.writeValueAsString(fetchedBidInfo)).willReturn("newJsonBid");

            // Mocking mapping to LotDetailsResponse
            given(lotMapper.toDetailsResponse(fetchedLotInfo, fetchedBidInfo)).willReturn(expectedDetailsResponse);

            LotDetailsResponse actualDetailsResponse = service.getLotDetails(lotId);

            assertThat(actualDetailsResponse).isEqualTo(expectedDetailsResponse);

            verify(lotRepository, times(1)).findById(any(UUID.class));
            verify(valueOperations).set(eq(lotInfoKey), eq("newJsonLot"), anyLong(), any(TimeUnit.class));
            verify(valueOperations).set(eq(bidInfoKey), eq("newJsonBid"), anyLong(), any(TimeUnit.class));
            verify(lotMapper).toDetailsResponse(eq(fetchedLotInfo), eq(fetchedBidInfo));
        }

        @Test
        void shouldThrowLotNotFoundException_WhenCacheIsEmptyAndLotNotFoundInDb() {
            given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(lotInfoKey)).willReturn(null);
            given(valueOperations.get(bidInfoKey)).willReturn(null);
            given(lotRepository.findById(lotId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getLotDetails(lotId))
                    .isInstanceOf(LotNotFoundException.class)
                    .hasMessageContaining(lotId.toString());

            verify(lotRepository, times(1)).findById(lotId);
            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
        }

        @Test
        void shouldEvictCorruptedCacheAndReturnDataFromDb_WhenJsonProcessingExceptionOccursOnRead() throws JsonProcessingException {
            LotEntity storedEntity = new LotEntity();
            storedEntity.setId(lotId);
            storedEntity.setCurrentMaxBid(BigDecimal.valueOf(1000));
            storedEntity.setHighestBidderId(UUID.randomUUID());

            LotResponse lotResponse = makeLotResponse(lotId, UUID.randomUUID());
            CurrentBidInfo bidInfo = new CurrentBidInfo(storedEntity.getCurrentMaxBid(), storedEntity.getHighestBidderId());
            LotDetailsResponse expectedDetails = makeLotDetailsResponse(lotResponse, bidInfo);

            given(valueOperations.get(lotInfoKey)).willReturn("corrupted-json");
            given(redisObjectMapper.readValue(eq("corrupted-json"), eq(LotResponse.class)))
                    .willThrow(new JsonProcessingException("Read failed") {});

            // CATCH block logic
            given(lotRepository.findById(lotId)).willReturn(Optional.of(storedEntity));
            given(lotMapper.toResponse(storedEntity)).willReturn(lotResponse);
            given(lotMapper.toDetailsResponse(eq(lotResponse), any(CurrentBidInfo.class))).willReturn(expectedDetails);

            LotDetailsResponse actualResponse = service.getLotDetails(lotId);

            assertThat(actualResponse).isEqualTo(expectedDetails);

            verify(stringRedisTemplate).delete(lotInfoKey);
            verify(stringRedisTemplate).delete(bidInfoKey);
            verify(lotRepository, times(1)).findById(lotId);
            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
        }

        @Test
        void shouldThrowLotNotFoundException_WhenJsonProcessingOccursOnReadAndLotNotFoundInDb() throws JsonProcessingException {
            given(valueOperations.get(lotInfoKey)).willReturn("corrupted-json");
            given(redisObjectMapper.readValue(eq("corrupted-json"), eq(LotResponse.class)))
                    .willThrow(new JsonProcessingException("Read failed") {});

            // CATCH block logic
            given(lotRepository.findById(lotId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getLotDetails(lotId))
                    .isInstanceOf(LotNotFoundException.class)
                    .hasMessageContaining(lotId.toString());

            verify(stringRedisTemplate).delete(lotInfoKey);
            verify(stringRedisTemplate).delete(bidInfoKey);
            verify(lotRepository, times(1)).findById(lotId);
        }

        private LotDetailsResponse makeLotDetailsResponse(LotResponse response, CurrentBidInfo bidInfo) {
            return new LotDetailsResponse(
                    response.lotId(),
                    response.ownerId(),
                    response.name(),
                    response.description(),
                    response.startPrice(),
                    response.minBidStep(),
                    bidInfo.currentMaxBid(),
                    bidInfo.highestBidderId(),
                    response.status(),
                    response.finishedAt(),
                    response.createdAt(),
                    response.updatedAt()
            );
        }

        private LotResponse makeLotResponse(UUID lotId, UUID userId) {
            return new LotResponse(
                    lotId, userId, "Name", "Description",
                    BigDecimal.valueOf(1000.00), BigDecimal.valueOf(5.00), LotStatus.ACTIVE,
                    now.plusDays(15), now.minusMinutes(1), now.minusMinutes(1)
            );
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
            verify(stringRedisTemplate, times(2)).delete(anyString());

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

            verifyNoInteractions(lotRepository, lotMapper, stringRedisTemplate);
        }

        @Test
        void shouldThrowException_WhenLotNotFoundByRequestedId() {
            UUID requestedLotId = UUID.randomUUID();

            given(lotRepository.findById(requestedLotId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelAuctionLot(requestedLotId, UUID.randomUUID(), UserStatus.ACTIVE))
                    .isInstanceOf(LotNotFoundException.class)
                    .hasMessageContaining("Lot with id " + requestedLotId + " not found.");

            verifyNoMoreInteractions(lotRepository);
            verifyNoInteractions(lotMapper, stringRedisTemplate);
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
            verifyNoInteractions(lotMapper, stringRedisTemplate);
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
            verifyNoInteractions(lotMapper, stringRedisTemplate);
        }
    }
}
