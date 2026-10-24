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
}
