package com.ahicode.bidflow.bidding.repositories;

import com.ahicode.bidflow.bidding.entities.LotEntity;
import com.ahicode.bidflow.bidding.enums.LotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LotRepository extends JpaRepository<LotEntity, UUID> {
    Optional<LotEntity> findById(UUID uuid);
    Page<LotEntity> findByStatus(LotStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LotEntity l WHERE l.id = :id")
    Optional<LotEntity> findByIdWithLock(@Param("id") UUID id);
}
