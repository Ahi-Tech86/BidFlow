package com.ahicode.bidflow.bidding.services;

import com.ahicode.bidflow.bidding.dtos.BidRequest;
import com.ahicode.bidflow.bidding.dtos.BidResponse;
import com.ahicode.bidflow.common.enums.UserStatus;

import java.util.UUID;

public interface BidService {
    BidResponse placeBid(BidRequest request, UUID lotId, UUID userId, UserStatus userStatus);
}
