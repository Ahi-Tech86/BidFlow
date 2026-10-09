package com.ahicode.bidflow.auth.services.schedulers;

import com.ahicode.bidflow.auth.repositories.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TokenCleanupSchedulerTest {

    @Captor private ArgumentCaptor<ZonedDateTime> dateCaptor;
    @Mock private RefreshTokenRepository repository;

    private TokenCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TokenCleanupScheduler(repository);
        ReflectionTestUtils.setField(scheduler, "retentionDays", 30);
    }

    @Test
    void shouldDeleteExpiredTokens() {
        when(repository.deleteAllByRevokedTrueAndCreatedAtBefore(any(ZonedDateTime.class))).thenReturn(5);
        when(repository.deleteAllByExpiresAtBefore(any(ZonedDateTime.class))).thenReturn(10);

        scheduler.cleanupExpiredTokens();

        verify(repository, times(1)).deleteAllByRevokedTrueAndCreatedAtBefore(dateCaptor.capture());
        ZonedDateTime capturedRevokedDate = dateCaptor.getValue();
        assertThat(capturedRevokedDate).isCloseTo(ZonedDateTime.now().minusDays(30), within(2, ChronoUnit.SECONDS));

        verify(repository, times(1)).deleteAllByExpiresAtBefore(dateCaptor.capture());
        ZonedDateTime capturedExpiredDate = dateCaptor.getValue();
        assertThat(capturedExpiredDate).isCloseTo(ZonedDateTime.now().minusDays(30), within(2, ChronoUnit.SECONDS));
    }

    @Test
    void shouldHandleZeroDeletedTokens() {
        when(repository.deleteAllByRevokedTrueAndCreatedAtBefore(any(ZonedDateTime.class))).thenReturn(0);
        when(repository.deleteAllByExpiresAtBefore(any(ZonedDateTime.class))).thenReturn(0);

        scheduler.cleanupExpiredTokens();

        verify(repository, times(1)).deleteAllByRevokedTrueAndCreatedAtBefore(any(ZonedDateTime.class));
        verify(repository, times(1)).deleteAllByExpiresAtBefore(any(ZonedDateTime.class));
    }
}
