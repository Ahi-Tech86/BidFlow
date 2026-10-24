package com.ahicode.bidflow.bidding.services;

import com.ahicode.bidflow.bidding.dtos.BidRequest;
import com.ahicode.bidflow.bidding.dtos.BidResponse;
import com.ahicode.bidflow.bidding.dtos.CreateLotRequest;
import com.ahicode.bidflow.bidding.dtos.LotResponse;
import com.ahicode.bidflow.common.enums.UserStatus;

import java.util.UUID;

public interface LotService {
    LotResponse createLot(CreateLotRequest request, UUID userId, UserStatus userStatus);
    BidResponse makeBid(BidRequest request, UUID lotId, UUID userId, UserStatus userStatus);
}
