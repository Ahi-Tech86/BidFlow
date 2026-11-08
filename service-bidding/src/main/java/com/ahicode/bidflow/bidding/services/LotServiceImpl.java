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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LotServiceImpl implements LotService {

    private static final String LOT_INFO_KEY_PREFIX = "lots:";
    private static final String BID_KEY_PREFIX = "lot:bids:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper redisObjectMapper;

    private final LotRepository lotRepository;
    private final BidRepository bidRepository;
    private final LotMapper lotMapper;
    private final BidMapper bidMapper;

    @Override
    @Transactional
    public LotResponse createLot(CreateLotRequest request, UUID userId, UserStatus userStatus) {
        if (userStatus != UserStatus.ACTIVE) {
            throw new UserNotAllowedException();
        }

        LotEntity entity = lotMapper.toEntity(request);
        entity.setOwnerId(userId);
        entity.setCurrentMaxBid(entity.getStartPrice());

        LotEntity savedLot = lotRepository.save(entity);
        log.info("Lot has been saved with ID: {}", savedLot.getId());

        LotResponse response = lotMapper.toResponse(savedLot);
        CurrentBidInfo initialBidInfo = new CurrentBidInfo(savedLot.getStartPrice(), null);

        try {
            String lotJson = redisObjectMapper.writeValueAsString(response);
            String bidJson = redisObjectMapper.writeValueAsString(initialBidInfo);

            stringRedisTemplate.opsForValue()
                    .set(LOT_INFO_KEY_PREFIX + savedLot.getId(), lotJson, 1, TimeUnit.HOURS);
            stringRedisTemplate.opsForValue()
                    .set(BID_KEY_PREFIX + savedLot.getId(), bidJson, 30, TimeUnit.MINUTES);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize lot data for Redis. Lot ID: {}", savedLot.getId());
            throw new CacheSerializationException("Internal server error during cache serialization", e);
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LotListItemResponse> getLotsByStatus(LotStatus lotStatus, Pageable pageable) {
        Page<LotEntity> lots = lotStatus != null
                ? lotRepository.findByStatus(lotStatus, pageable)
                : lotRepository.findByStatus(LotStatus.ACTIVE, pageable);

        return PageResponse.from(lots.map(lotMapper::toListItemResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public LotDetailsResponse getLotDetails(UUID lotId) {
        String lotInfoKey = LOT_INFO_KEY_PREFIX + lotId;
        String bidInfoKey = BID_KEY_PREFIX + lotId;

        try {
            String lotJson = stringRedisTemplate.opsForValue().get(lotInfoKey);
            String bidJson = stringRedisTemplate.opsForValue().get(bidInfoKey);

            LotResponse lotInfo = null;
            CurrentBidInfo bidInfo = null;

            if (lotJson != null) {
                lotInfo = redisObjectMapper.readValue(lotJson, LotResponse.class);
            }
            if (bidJson != null) {
                bidInfo = redisObjectMapper.readValue(bidJson, CurrentBidInfo.class);
            }

            if (lotInfo == null || bidInfo == null) {
                LotEntity storedLot = lotRepository.findById(lotId)
                        .orElseThrow(() -> new LotNotFoundException(lotId));

                if (lotInfo == null) {
                    lotInfo = lotMapper.toResponse(storedLot);
                    stringRedisTemplate.opsForValue()
                            .set(lotInfoKey, redisObjectMapper.writeValueAsString(lotInfo), 1, TimeUnit.HOURS);
                }

                if (bidInfo == null) {
                    bidInfo = new CurrentBidInfo(storedLot.getCurrentMaxBid(), storedLot.getHighestBidderId());
                    stringRedisTemplate.opsForValue()
                            .set(bidInfoKey, redisObjectMapper.writeValueAsString(bidInfo), 30, TimeUnit.MINUTES);
                }
            }

            return lotMapper.toDetailsResponse(lotInfo, bidInfo);

        }  catch (JsonProcessingException e) {
            log.error("Corrupted JSON in Redis for lotId: {}. Evicting keys and falling back to DB.", lotId, e);

            // 1. Evicting broken cache
            stringRedisTemplate.delete(lotInfoKey);
            stringRedisTemplate.delete(bidInfoKey);

            // 2. Getting entity from DB
            LotEntity storedEntity = lotRepository.findById(lotId)
                    .orElseThrow(() -> new LotNotFoundException(lotId));

            // 3. Return response to client
            return lotMapper.toDetailsResponse(
                    lotMapper.toResponse(storedEntity),
                    new CurrentBidInfo(storedEntity.getCurrentMaxBid(), storedEntity.getHighestBidderId())
            );
        }
    }

    @Override
    @Transactional
    public LotResponse cancelAuctionLot(UUID lotId, UUID userId, UserStatus userStatus) {
        if (userStatus != UserStatus.ACTIVE) {
            throw new UserNotAllowedException();
        }

        LotEntity lotEntity = lotRepository.findById(lotId)
                .orElseThrow(() -> new LotNotFoundException(lotId));

        if (!lotEntity.getOwnerId().equals(userId)) {
            throw new OwnerCannotCancelException();
        }

        if (lotEntity.getStatus() != LotStatus.ACTIVE) {
            throw new LotNotActiveException(lotId, lotEntity.getStatus());
        }

        lotEntity.setStatus(LotStatus.CANCELLED);
        LotEntity savedLot = lotRepository.save(lotEntity);

        // Invalidate cache
        stringRedisTemplate.delete(LOT_INFO_KEY_PREFIX + lotId);
        stringRedisTemplate.delete(BID_KEY_PREFIX + lotId);

        return lotMapper.toResponse(savedLot);
    }

    @Override
    @Transactional
    public BidResponse makeBid(BidRequest request, UUID lotId, UUID userId, UserStatus userStatus) {
        if (userStatus != UserStatus.ACTIVE) {
            throw new UserNotAllowedException();
        }

        LotEntity lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new LotNotFoundException(lotId));

        if (lot.getOwnerId().equals(userId)) {
            throw new OwnerCannotBidException();
        }

        if (lot.getStatus() != LotStatus.ACTIVE) {
            throw new LotNotActiveException(lotId, lot.getStatus());
        }

        // 1. The bid should be greater than start price
        // request.bidValue() > lot.getStartPrice() <=> startPrice.compareTo(bidValue) < 0
        if (lot.getStartPrice().compareTo(request.bidValue()) >= 0) {
            throw new BidTooLowException("Bid must be greater than start price");
        }

        // 2. The bid should be more or equals than current max bid + minimal bid step
        BigDecimal maxCurrentBid = bidRepository.findMaxBidForLotByLotId(lotId);
        BigDecimal minAllowedBid = maxCurrentBid.add(lot.getMinBidStep());

        // minAllowedBid > request.bidValue()  <=>  minAllowedBid.compareTo(request.bidValue()) > 0
        if (minAllowedBid.compareTo(request.bidValue()) > 0) {
            throw new BidTooLowException("Bid increment must be at least " + lot.getMinBidStep());
        }

        BidEntity entity = bidMapper.toEntity(request);
        entity.setLotId(lotId);
        entity.setUserId(userId);

        BidEntity savedBid = bidRepository.save(entity);

        // TODO - add Redis cache update and event publish in Kafka

        return bidMapper.toResponse(savedBid);
    }
}
