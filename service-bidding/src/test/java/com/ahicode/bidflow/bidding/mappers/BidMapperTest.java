package com.ahicode.bidflow.bidding.mappers;

import com.ahicode.bidflow.bidding.dtos.BidRequest;
import com.ahicode.bidflow.bidding.dtos.BidResponse;
import com.ahicode.bidflow.bidding.entities.BidEntity;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class BidMapperTest {

    private final BidMapper mapper = Mappers.getMapper(BidMapper.class);

    @Nested
    class ToEntity {
        @Test
        void shouldMapOnlyAllowedFields_FromBidRequest() {
            BidRequest request = new BidRequest(BigDecimal.valueOf(45_000.00));

            BidEntity entity = mapper.toEntity(request);

            assertThat(entity)
                    .isNotNull()
                    .extracting(
                            BidEntity::getId, BidEntity::getLotId, BidEntity::getUserId,
                            BidEntity::getBidValue, BidEntity::getCreatedAt
                    )
                    .containsExactly(
                            null, null, null, request.bidValue(), null
                    );
        }
    }

    @Nested
    class ToResponse {
        @Test
        void shouldMapOnlyAllowedFields_FromBidEntity() {
            ZonedDateTime now = ZonedDateTime.now();
            UUID bidId = UUID.randomUUID();
            UUID lotId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            BidEntity entity = new BidEntity(
                    bidId,
                    lotId,
                    userId,
                    BigDecimal.valueOf(100_000.00),
                    now.minusMinutes(1)
            );

            BidResponse response = mapper.toResponse(entity);

            assertThat(response)
                    .isNotNull()
                    .extracting(
                            BidResponse::id, BidResponse::lotId, BidResponse::userId,
                            BidResponse::bidValue, BidResponse::createdAt
                    )
                    .containsExactly(
                            bidId, lotId, userId, BigDecimal.valueOf(100_000.00), now.minusMinutes(1)
                    );
        }
    }
}
