package com.ahicode.bidflow.bidding.repositories;

import com.ahicode.bidflow.bidding.entities.LotEntity;
import com.ahicode.bidflow.bidding.enums.LotStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LotRepository extends JpaRepository<LotEntity, UUID> {
    Optional<LotEntity> findById(UUID uuid);
    Page<LotEntity> findByStatus(LotStatus status, Pageable pageable);
}
