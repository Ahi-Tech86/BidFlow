package com.ahicode.bidflow.auth.services.schedulers;

import com.ahicode.bidflow.auth.repositories.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    @Value("${application.auth.token-cleanup.retention-days:30}")
    private Integer retentionDays;

    private final RefreshTokenRepository repository;

    @Scheduled(cron = "0 0 3 * * ?")    // every day at 3:00am
    @SchedulerLock(name = "cleanupExpiredTokens", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    public void cleanupExpiredTokens() {
        log.info("[CLEANUP] Starting token cleanup task...");

        ZonedDateTime now = ZonedDateTime.now();

        int revokedDeleted = repository.deleteAllByRevokedTrueAndCreatedAtBefore(now.minusDays(retentionDays));
        log.info("[CLEANUP] Deleted {} old revoked tokens", revokedDeleted);

        int expiredDeleted = repository.deleteAllByExpiresAtBefore(now.minusDays(retentionDays));
        log.info("[CLEANUP] Deleted {} expired tokens", expiredDeleted);
    }
}
