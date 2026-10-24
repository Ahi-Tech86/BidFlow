package com.ahicode.bidflow.bidding.repositories;

import com.ahicode.bidflow.bidding.entities.BidEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface BidRepository extends JpaRepository<BidEntity, UUID> {

    @Query("SELECT COALESCE(MAX(b.bidValue), 0) FROM BidEntity b WHERE b.lotId = :lotId")
    BigDecimal findMaxBidForLotByLotId(@Param("lotId") UUID lotId);
}
