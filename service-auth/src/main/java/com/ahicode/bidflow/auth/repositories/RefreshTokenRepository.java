package com.ahicode.bidflow.auth.repositories;

import com.ahicode.bidflow.auth.entities.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    List<RefreshTokenEntity> findAllByUserIdAndRevokedFalse(UUID userId);
    Optional<RefreshTokenEntity> findByJti(String jti);
}
