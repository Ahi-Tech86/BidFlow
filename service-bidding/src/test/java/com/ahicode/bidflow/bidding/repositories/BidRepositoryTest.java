package com.ahicode.bidflow.bidding.repositories;

import com.ahicode.bidflow.bidding.TestContainersConfiguration;
import com.ahicode.bidflow.bidding.entities.BidEntity;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestContainersConfiguration.class)
public class BidRepositoryTest {

    private LotEntity savedLot;

    @Autowired private TestEntityManager entityManager;
    @Autowired private BidRepository repository;

    @BeforeEach
    void setUp() {
        ZonedDateTime now = ZonedDateTime.now();

        LotEntity lot = new LotEntity(
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

        savedLot = entityManager.persistAndFlush(lot);
        entityManager.clear();
    }

    @Nested
    class FindMaxBidForLotByLotId {
        private final ZonedDateTime now = ZonedDateTime.now();

        @Test
        void shouldReturnMaxBidValueForLot() {
            for (int i = 0; i < 5; i++) {
                BidEntity bid = new BidEntity(
                        null,
                        savedLot.getId(),
                        UUID.randomUUID(),
                        BigDecimal.valueOf(100_000.00 + (500.00 * i) ),
                        now.minusMinutes(i)
                );

                entityManager.persistAndFlush(bid);
            }
            entityManager.clear();

            BigDecimal expectedMaxBidValue = repository.findMaxBidForLotByLotId(savedLot.getId());

            assertThat(expectedMaxBidValue).isGreaterThanOrEqualTo(BigDecimal.valueOf(102_000.00));
        }

        @Test
        void shouldReturnZero_WhenNoBidsForLot() {
            BigDecimal expectedMaxBidValue = repository.findMaxBidForLotByLotId(savedLot.getId());

            assertThat(expectedMaxBidValue).isEqualTo(BigDecimal.ZERO);
        }
    }
}
