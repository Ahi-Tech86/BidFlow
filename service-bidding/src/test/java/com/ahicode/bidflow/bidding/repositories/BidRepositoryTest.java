package com.ahicode.bidflow.bidding.repositories;

import com.ahicode.bidflow.bidding.TestContainersConfiguration;
import com.ahicode.bidflow.bidding.entities.BidEntity;
import com.ahicode.bidflow.bidding.entities.LotEntity;
import com.ahicode.bidflow.bidding.enums.LotStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
                BigDecimal.valueOf(100_000.00),
                null,
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

    @Nested
    @DisplayName("countByLotId")
    class CountByLotId {
        private final ZonedDateTime now = ZonedDateTime.now();

        @Test
        void shouldReturnCorrectBidCount_WhenLotHasMultipleBids() {
            LotEntity secondLot = new LotEntity(
                    null,
                    UUID.randomUUID(),
                    "Casio Watch",
                    "Classic watch",
                    BigDecimal.valueOf(5_000.00),
                    LotStatus.ACTIVE,
                    BigDecimal.valueOf(50.00),
                    BigDecimal.valueOf(5_000.00),
                    null,
                    now.plusDays(14),
                    now.minusDays(5),
                    now.minusDays(5)
            );
            entityManager.persistAndFlush(secondLot);
            entityManager.clear();

            for (int i = 1; i < 6; i++) {
                BidEntity bid = new BidEntity();
                bid.setId(null);
                bid.setUserId(UUID.randomUUID());
                bid.setCreatedAt(now.minusDays(i));

                if (i == 5) {
                    bid.setLotId(secondLot.getId());
                    bid.setBidValue(secondLot.getStartPrice().add(BigDecimal.valueOf(100 * i)));
                } else {
                    bid.setLotId(savedLot.getId());
                    bid.setBidValue(savedLot.getStartPrice().add(BigDecimal.valueOf(1000 * i)));
                }

                entityManager.persistAndFlush(bid);
            }
            entityManager.clear();

            Long numberOfBids = repository.countByLotId(savedLot.getId());

            assertThat(numberOfBids).isEqualTo(4);
        }

        @Test
        void shouldReturnZero_WhenLotHasNoBids() {
            Long numOfBid = repository.countByLotId(savedLot.getId());

            assertThat(numOfBid).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("getAllByLotId")
    class GetAllByLotId {

        @Test
        @DisplayName("should return a page of bids when bids exist for the lot")
        void shouldReturnPageOfBids_WhenBidsExist() {
            UUID lotId = savedLot.getId();

            BidEntity bid1 = createBid(lotId, BigDecimal.valueOf(101_000));
            BidEntity bid2 = createBid(lotId, BigDecimal.valueOf(102_000));
            BidEntity bid3 = createBid(lotId, BigDecimal.valueOf(103_000));

            entityManager.persist(bid1);
            entityManager.persist(bid2);
            entityManager.persist(bid3);
            entityManager.flush();
            entityManager.clear();

            Pageable pageable = PageRequest.of(0, 2);

            Page<BidEntity> result = repository.getAllByLotId(lotId, pageable);
            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getTotalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return an empty page when no bids exist for the lot")
        void shouldReturnEmptyPage_WhenNoBidsExist() {
            UUID lotId = savedLot.getId();
            Pageable pageable = PageRequest.of(0, 10);

            Page<BidEntity> result = repository.getAllByLotId(lotId, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getTotalPages()).isZero();
        }

        @Test
        @DisplayName("should return an empty page when the lot does not exist")
        void shouldReturnEmptyPage_WhenLotDoesNotExist() {
            UUID nonExistentLotId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);

            Page<BidEntity> result = repository.getAllByLotId(nonExistentLotId, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("should correctly apply sorting from Pageable")
        void shouldCorrectlyApplySortingFromPageable() {
            UUID lotId = savedLot.getId();
            BidEntity bid1 = createBid(lotId, BigDecimal.valueOf(100_000));
            BidEntity bid2 = createBid(lotId, BigDecimal.valueOf(200_000));

            entityManager.persist(bid1);
            entityManager.persist(bid2);
            entityManager.flush();
            entityManager.clear();

            Pageable pageable = PageRequest.of(0, 10, org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.DESC, "bidValue"
            ));

            Page<BidEntity> result = repository.getAllByLotId(lotId, pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getBidValue()).isEqualByComparingTo(BigDecimal.valueOf(200_000));
            assertThat(result.getContent().get(1).getBidValue()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
        }

        private BidEntity createBid(UUID lotId, BigDecimal bidValue) {
            return new BidEntity(
                    null,
                    lotId,
                    UUID.randomUUID(),
                    bidValue,
                    ZonedDateTime.now()
            );
        }
    }
}
