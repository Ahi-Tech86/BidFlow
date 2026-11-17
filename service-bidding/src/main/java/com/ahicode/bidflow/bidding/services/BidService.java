package com.ahicode.bidflow.bidding.services;

import com.ahicode.bidflow.bidding.dtos.BidHistoryResponse;
import com.ahicode.bidflow.bidding.dtos.BidRequest;
import com.ahicode.bidflow.bidding.dtos.BidResponse;
import com.ahicode.bidflow.bidding.dtos.PageResponse;
import com.ahicode.bidflow.common.enums.UserStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BidService {
    BidResponse placeBid(BidRequest request, UUID lotId, UUID userId, UserStatus userStatus);
    PageResponse<BidHistoryResponse> getBidsHistory(UUID lotId, Pageable pageable);
}
