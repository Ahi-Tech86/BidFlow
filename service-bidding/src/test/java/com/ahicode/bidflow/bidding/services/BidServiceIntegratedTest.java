package com.ahicode.bidflow.bidding.services;

import com.ahicode.bidflow.bidding.TestContainersConfiguration;
import com.ahicode.bidflow.bidding.dtos.BidRequest;
import com.ahicode.bidflow.bidding.dtos.BidResponse;
import com.ahicode.bidflow.bidding.dtos.CurrentBidInfo;
import com.ahicode.bidflow.bidding.entities.LotEntity;
import com.ahicode.bidflow.bidding.enums.LotStatus;
import com.ahicode.bidflow.bidding.exceptions.BidTooLowException;
import com.ahicode.bidflow.bidding.repositories.BidRepository;
import com.ahicode.bidflow.bidding.repositories.LotRepository;
import com.ahicode.bidflow.common.enums.UserStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(TestContainersConfiguration.class)
public class BidServiceIntegratedTest {

    @Autowired private StringRedisTemplate stringRedisTemplate;
    @Autowired private ObjectMapper redisObjectMapper;
    @Autowired private LotRepository lotRepository;
    @Autowired private BidRepository bidRepository;
    @Autowired private BidService bidService;

    @BeforeEach
    void setUp() {
        stringRedisTemplate.execute((RedisConnection connection) -> {
            connection.flushAll();
            return null;
        });
    }

    @Nested
    class PlacedBid {
        private final ZonedDateTime now = ZonedDateTime.now();
        private final UUID user1Id = UUID.randomUUID();
        private final UUID user2Id = UUID.randomUUID();
        private UUID lotId;

        private static final String BID_KEY_PREFIX = "lot:bids:";

        @BeforeEach
        void setUp() {
            LotEntity lotEntityToSave = LotEntity.builder()
                    .id(null)
                    .ownerId(UUID.randomUUID())
                    .name("Name")
                    .description("Description")
                    .startPrice(BigDecimal.valueOf(1500.00))
                    .status(LotStatus.ACTIVE)
                    .minBidStep(BigDecimal.valueOf(10.00))
                    .currentMaxBid(BigDecimal.valueOf(1500.00))
                    .highestBidderId(null)
                    .finishedAt(now.plusDays(15))
                    .createdAt(now.minusHours(4))
                    .updatedAt(now.minusHours(4))
                    .build();

            LotEntity savedLotEntity = lotRepository.save(lotEntityToSave);
            lotId = savedLotEntity.getId();
        }

        @Test
        void concurrentBids_ShouldHandlePessimisticLockCorrectly() throws Exception {
            // readyLatch awaits main thread
            CountDownLatch readyLatch = new CountDownLatch(1);
            // doneLatch awaits worker threads
            CountDownLatch doneLatch = new CountDownLatch(2);

            ExecutorService executorService = Executors.newFixedThreadPool(2);

            // Results container for each thread
            AtomicReference<BidResponse> result1 = new AtomicReference<>();
            AtomicReference<Throwable> error1 = new AtomicReference<>();
            AtomicReference<BidResponse> result2 = new AtomicReference<>();
            AtomicReference<Throwable> error2 = new AtomicReference<>();

            // Task for 1st user
            Runnable firstBidderTask = () -> {
                try {
                    readyLatch.await();

                    BidResponse response = bidService.placeBid(
                            new BidRequest(BigDecimal.valueOf(1600.00)),
                            lotId,
                            user1Id,
                            UserStatus.ACTIVE
                    );
                    result1.set(response);

                } catch (Exception e) {
                    error1.set(e);
                } finally {
                    doneLatch.countDown();
                }
            };

            // Task for 2nd user
            Runnable secondBidderTask = () -> {
                try {
                    readyLatch.await();

                    BidResponse response = bidService.placeBid(
                            new BidRequest(BigDecimal.valueOf(1600.00)),
                            lotId,
                            user2Id,
                            UserStatus.ACTIVE
                    );
                    result2.set(response);

                } catch (Exception e) {
                    error2.set(e);
                } finally {
                    doneLatch.countDown();
                }
            };

            // Running and synch
            executorService.submit(firstBidderTask);
            executorService.submit(secondBidderTask);

            Thread.sleep(50);
            readyLatch.countDown();

            // Waiting 5s when threads will finish execution
            boolean finishedInTime = doneLatch.await(5, TimeUnit.SECONDS);
            assertTrue(finishedInTime, "Threads didn't finished on time; a deadlock may have occurred.");
            executorService.shutdown();

            // ==========================================
            // Assert: Concurrency checking
            // ==========================================
            boolean user1Succeeded = result1.get() != null && error1.get() == null;
            boolean user2Succeeded = result2.get() != null && error2.get() == null;

            assertTrue(user1Succeeded ^ user2Succeeded, "Only one user should place bid (XOR)");

            Throwable failedUserError = user1Succeeded ? error2.get() : error1.get();
            assertNotNull(failedUserError, "Second user must get error");
            assertInstanceOf(
                    BidTooLowException.class, failedUserError,
                    "Expected BidTooLowException, but got: " + failedUserError.getClass().getSimpleName()
            );

            LotEntity updatedLot = lotRepository.findById(lotId).orElseThrow();
            assertEquals(
                    0, updatedLot.getCurrentMaxBid().compareTo(BigDecimal.valueOf(1600.00)),
                    "Current max bid should be greater than 1600.00"
            );

            UUID winnerId = user1Succeeded ? user1Id : user2Id;
            assertEquals(
                    winnerId, updatedLot.getHighestBidderId(),
                    "The ID of the current winner in the database must match the ID of the successful user"
            );

            long bidsCount = bidRepository.countByLotId(lotId);
            assertEquals(
                    1, bidsCount,
                    "There must be exactly one entry in the `bids` table for this lot"
            );

            // ==========================================
            // Assert: Cache refresh checking (Redis)
            // ==========================================
            String expectedCacheKey = BID_KEY_PREFIX + lotId;

            String cachedJson = stringRedisTemplate.opsForValue().get(expectedCacheKey);
            assertNotNull(cachedJson, "Cache for bid should be updated after successfully placed bid");

            try {
                CurrentBidInfo cachedBidInfo = redisObjectMapper.readValue(cachedJson, CurrentBidInfo.class);

                assertEquals(
                        0, cachedBidInfo.currentMaxBid().compareTo(BigDecimal.valueOf(1600.00)),
                        "Cache should contains new max bid 1600.00"
                );
                assertEquals(
                        winnerId, cachedBidInfo.highestBidderId(), "Cache should contains winner ID"
                );

            } catch (JsonProcessingException e) {
                fail("Failed deserialize bid from cache: " + e.getMessage());
            }
        }
    }
}
