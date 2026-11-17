package com.ahicode.bidflow.bidding.services;

import com.ahicode.bidflow.bidding.dtos.*;
import com.ahicode.bidflow.bidding.entities.BidEntity;
import com.ahicode.bidflow.bidding.entities.LotEntity;
import com.ahicode.bidflow.bidding.enums.LotStatus;
import com.ahicode.bidflow.bidding.exceptions.*;
import com.ahicode.bidflow.bidding.mappers.BidMapper;
import com.ahicode.bidflow.bidding.repositories.BidRepository;
import com.ahicode.bidflow.bidding.repositories.LotRepository;
import com.ahicode.bidflow.common.enums.UserStatus;
import com.ahicode.bidflow.common.exceptions.UserNotAllowedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidServiceImpl implements BidService {

    private static final String LOT_INFO_KEY_PREFIX = "lots:";
    private static final String BID_KEY_PREFIX = "lot:bids:";

    private final ApplicationEventPublisher eventPublisher;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper redisObjectMapper;

    private final LotRepository lotRepository;
    private final BidRepository bidRepository;
    private final BidMapper bidMapper;

    @Override
    @Transactional
    public BidResponse placeBid(BidRequest request, UUID lotId, UUID userId, UserStatus userStatus) {
        if (userStatus != UserStatus.ACTIVE) {
            throw new UserNotAllowedException();
        }

        // Getting pessimistic lock
        LotEntity storedLot = lotRepository.findByIdWithLock(lotId)
                .orElseThrow(() -> new LotNotFoundException(lotId));

        if (storedLot.getOwnerId().equals(userId)) {
            throw new OwnerCannotBidException();
        }

        BigDecimal minAllowedBid = getMinAllowedBid(request, lotId, storedLot);

        // minAllowedBid > request.newMaxBid()  <=>  minAllowedBid.compareTo(request.newMaxBid()) > 0
        if (minAllowedBid.compareTo(request.bidValue()) > 0) {
            throw new BidTooLowException("Bid increment must be at least " + storedLot.getMinBidStep());
        }

        BidEntity entity = bidMapper.toEntity(request);
        entity.setLotId(lotId);
        entity.setUserId(userId);

        BidEntity savedBid = bidRepository.save(entity);

        // Updating new current max bid value and highest bidder ID in lots table
        storedLot.setCurrentMaxBid(request.bidValue());
        storedLot.setHighestBidderId(userId);
        lotRepository.save(storedLot);

        eventPublisher.publishEvent(new BidPlacedEvent(lotId, request.bidValue(), userId));

        return bidMapper.toResponse(savedBid);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BidHistoryResponse> getBidsHistory(UUID lotId, Pageable pageable) {
        Page<BidEntity> bidEntityPage = bidRepository.getAllByLotId(lotId, pageable);
        return PageResponse.from(bidEntityPage.map(bidMapper::toHistoryResponse));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    protected void refreshBidCache(BidPlacedEvent event) {
        try {
            CurrentBidInfo bidInfo = new CurrentBidInfo(event.newMaxBid(), event.newBidderId());
            String jsonBidInfo = redisObjectMapper.writeValueAsString(bidInfo);

            stringRedisTemplate.opsForValue()
                    .set(BID_KEY_PREFIX + event.lotId(), jsonBidInfo, 30, TimeUnit.MINUTES);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize lot data for Redis. Lot ID: {}", event.lotId());
            throw new CacheSerializationException("Internal server error during cache serialization", e);
        }
    }

    private BigDecimal getMinAllowedBid(BidRequest request, UUID lotId, LotEntity storedLot) {
        if (storedLot.getStatus() != LotStatus.ACTIVE) {
            throw new LotNotActiveException(lotId, storedLot.getStatus());
        }

        // 1. The bid should be greater than start price
        // request.newMaxBid() > lot.getStartPrice() <=> startPrice.compareTo(newMaxBid) < 0
        if (storedLot.getStartPrice().compareTo(request.bidValue()) >= 0) {
            throw new BidTooLowException("Bid must be greater than start price");
        }

        // 2. The bid should be more or equals than current max bid + minimal bid step
        BigDecimal maxCurrentBid = storedLot.getCurrentMaxBid();
        return maxCurrentBid.add(storedLot.getMinBidStep());
    }
}
