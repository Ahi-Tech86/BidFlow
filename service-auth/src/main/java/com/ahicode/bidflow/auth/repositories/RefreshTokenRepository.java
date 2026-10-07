package com.ahicode.bidflow.auth.repositories;

import com.ahicode.bidflow.auth.entities.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    List<RefreshTokenEntity> findAllByUserIdAndRevokedFalse(UUID userId);
    List<RefreshTokenEntity> findAllByUserId(UUID userId);
    Optional<RefreshTokenEntity> findByJti(String jti);

    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.userId = :userId AND r.revoked = false")
    void revokeAllByUserId(@Param("userId") UUID userId);
}
