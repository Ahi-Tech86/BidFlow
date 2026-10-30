package com.ahicode.bidflow.bidding.repositories;

import com.ahicode.bidflow.bidding.TestContainersConfiguration;
import com.ahicode.bidflow.bidding.entities.LotEntity;
import com.ahicode.bidflow.bidding.enums.LotStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestContainersConfiguration.class)
public class LotRepositoryTest {

    private LotEntity lot;

    @Autowired private TestEntityManager entityManager;
    @Autowired private LotRepository repository;

    @BeforeEach
    void setUp() {
        ZonedDateTime now = ZonedDateTime.now();

        lot = new LotEntity(
                null,
                UUID.randomUUID(),
                "Ferrari F40",
                "Classic sport car",
                BigDecimal.valueOf(100_000.00),
                LotStatus.ACTIVE,
                BigDecimal.valueOf(500.00),
                now.plusDays(14),
                now.minusDays(2),
                now.minusDays(2)
        );
    }

    @Nested
    class FindById {
        @Test
        void shouldReturnOptionalLotById() {
            LotEntity savedLot = entityManager.persistAndFlush(lot);
            entityManager.clear();

            Optional<LotEntity> found = repository.findById(lot.getId());

            assertThat(found).isPresent();

            assertThat(found.get())
                    .extracting(LotEntity::getId, LotEntity::getName, LotEntity::getStatus)
                    .containsExactly(savedLot.getId(), savedLot.getName(), savedLot.getStatus());
        }

        @Test
        void shouldReturnEmptyOptional_WhenLotDoesNotExists() {
            Optional<LotEntity> found = repository.findById(UUID.randomUUID());

            assertThat(found.isEmpty());
        }
    }

    @Nested
    class FindByStatus {
        @Test
        void shouldReturnPageOfLotsWithSpecificStatus() {
            entityManager.persistAndFlush(createLot("Ferrari F40", LotStatus.ACTIVE));
            entityManager.persistAndFlush(createLot("Casio Watch", LotStatus.ACTIVE));
            entityManager.persistAndFlush(createLot("Picture", LotStatus.CANCELLED));
            entityManager.clear();

            Pageable pageable = PageRequest.of(0, 10);

            Page<LotEntity> result = repository.findByStatus(LotStatus.ACTIVE, pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(1);

            assertThat(result.getContent())
                    .extracting(LotEntity::getStatus)
                    .containsOnly(LotStatus.ACTIVE);
        }

        @Test
        void shouldReturnEmptyPage_WhenNoLotsWithStatus() {
            entityManager.persistAndFlush(createLot("Ferrari F40", LotStatus.CANCELLED));
            entityManager.persistAndFlush(createLot("Casio Watch", LotStatus.CANCELLED));
            entityManager.persistAndFlush(createLot("Picture", LotStatus.CANCELLED));
            entityManager.clear();

            Pageable pageable = PageRequest.of(0, 10);

            Page<LotEntity> result = repository.findByStatus(LotStatus.ACTIVE, pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getTotalPages()).isZero();
        }

        @Test
        void shouldRespectPaginationLimits() {
            entityManager.persistAndFlush(createLot("Ferrari F40", LotStatus.CANCELLED));
            entityManager.persistAndFlush(createLot("Casio Watch", LotStatus.CANCELLED));
            entityManager.persistAndFlush(createLot("Picture", LotStatus.CANCELLED));
            entityManager.clear();

            Pageable firstPage = PageRequest.of(0, 2);
            Page<LotEntity> resultFirst = repository.findByStatus(LotStatus.CANCELLED, firstPage);

            PageRequest secondPage = PageRequest.of(1, 2);
            Page<LotEntity> resultSecond = repository.findByStatus(LotStatus.CANCELLED, secondPage);

            assertThat(resultFirst.getContent()).hasSize(2);
            assertThat(resultFirst.getTotalElements()).isEqualTo(3);
            assertThat(resultFirst.getTotalPages()).isEqualTo(2);

            assertThat(resultSecond.getContent()).hasSize(1);
            assertThat(resultSecond.getTotalElements()).isEqualTo(3);
            assertThat(resultSecond.getTotalPages()).isEqualTo(2);
        }
    }

    private LotEntity createLot(String name, LotStatus status) {
        ZonedDateTime now = ZonedDateTime.now();

        return new LotEntity(
                null,
                UUID.randomUUID(),
                name,
                "Description for " + name,
                BigDecimal.valueOf(100_000.00),
                status,
                BigDecimal.valueOf(400.00),
                now.plusDays(4),
                now.minusMinutes(1),
                now.minusMinutes(1)
        );
    }
}
