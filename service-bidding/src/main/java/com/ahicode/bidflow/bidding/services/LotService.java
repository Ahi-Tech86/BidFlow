package com.ahicode.bidflow.bidding.services;

import com.ahicode.bidflow.bidding.dtos.*;
import com.ahicode.bidflow.bidding.enums.LotStatus;
import com.ahicode.bidflow.common.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface LotService {
    LotResponse createLot(CreateLotRequest request, UUID userId, UserStatus userStatus);
    LotResponse getLotDetails(UUID lotId);
    Page<LotListItemResponse> getLotsByStatus(LotStatus lotStatus, Pageable pageable);
    LotResponse cancelAuctionLot(UUID lotId, UUID userId, UserStatus userStatus);
    BidResponse makeBid(BidRequest request, UUID lotId, UUID userId, UserStatus userStatus);
}
