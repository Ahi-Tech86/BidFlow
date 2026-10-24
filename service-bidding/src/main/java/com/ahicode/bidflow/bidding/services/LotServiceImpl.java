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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public LotResponse createLot(CreateLotRequest request, UUID userId, UserStatus userStatus) {
        if (userStatus != UserStatus.ACTIVE) {
            throw new UserNotAllowedException();
        }

        LotEntity entity = lotMapper.toEntity(request);
        entity.setOwnerId(userId);

        LotEntity savedLot = lotRepository.save(entity);
        log.info("Lot has been saved with ID: {}", savedLot.getId());

        // TODO - add putting lot information in Redis cache

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
