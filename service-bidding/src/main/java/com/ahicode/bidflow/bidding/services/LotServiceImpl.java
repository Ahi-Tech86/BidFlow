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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LotServiceImpl implements LotService {

    private final LotRepository lotRepository;
    private final BidRepository bidRepository;
    private final LotMapper lotMapper;
    private final BidMapper bidMapper;

    @Override
    @Transactional
    @CachePut(value = "lots", key = "#result.lotId")
    public LotResponse createLot(CreateLotRequest request, UUID userId, UserStatus userStatus) {
        if (userStatus != UserStatus.ACTIVE) {
            throw new UserNotAllowedException();
        }

        LotEntity entity = lotMapper.toEntity(request);
        entity.setOwnerId(userId);

        LotEntity savedLot = lotRepository.save(entity);
        log.info("Lot has been saved with ID: {}", savedLot.getId());

        return lotMapper.toResponse(savedLot);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LotListItemResponse> getLotsByStatus(LotStatus lotStatus, Pageable pageable) {
        Page<LotEntity> lots = lotStatus != null
                ? lotRepository.findByStatus(lotStatus, pageable)
                : lotRepository.findByStatus(LotStatus.ACTIVE, pageable);

        return lots.map(lotMapper::toListItemResponse);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable (value = "lots", key = "#lotId")
    public LotResponse getLotDetails(UUID lotId) {
        LotEntity lotEntity = lotRepository.findById(lotId)
                .orElseThrow(() -> new LotNotFoundException(lotId));

        return lotMapper.toResponse(lotEntity);
    }

    @Override
    @Transactional
    @CacheEvict(value = "lots", key = "#lotId")
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
