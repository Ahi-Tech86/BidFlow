package com.ahicode.bidflow.bidding.mappers;

import com.ahicode.bidflow.bidding.dtos.CreateLotRequest;
import com.ahicode.bidflow.bidding.dtos.LotListItemResponse;
import com.ahicode.bidflow.bidding.dtos.LotResponse;
import com.ahicode.bidflow.bidding.entities.LotEntity;
import com.ahicode.bidflow.bidding.enums.LotStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class LotMapperTest {

    private final LotMapper mapper = Mappers.getMapper(LotMapper.class);

    @Nested
    class ToEntity {
        @Test
        void shouldMapOnlyAllowedFields_FromCreateLotRequest() {
            ZonedDateTime now = ZonedDateTime.now();

            CreateLotRequest request = new CreateLotRequest(
                    "Ferrari F40",
                    "Classic sport car",
                    BigDecimal.valueOf(100_000.00),
                    BigDecimal.valueOf(50.00),
                    now.plusDays(14)
            );

            LotEntity entity = mapper.toEntity(request);

            assertThat(entity)
                    .isNotNull()
                    .extracting(
                            LotEntity::getName, LotEntity::getDescription,
                            LotEntity::getStartPrice, LotEntity::getMinBidStep,
                            LotEntity::getFinishedAt
                    )
                    .containsExactly(
                            "Ferrari F40", "Classic sport car",
                            BigDecimal.valueOf(100_000.00), BigDecimal.valueOf(50.00),
                            now.plusDays(14)
                    );

            assertThat(entity)
                    .extracting(
                            LotEntity::getId, LotEntity::getOwnerId, LotEntity::getStatus,
                            LotEntity::getCreatedAt, LotEntity::getUpdatedAt
                    )
                    .containsExactly(null, null, LotStatus.ACTIVE, null, null);
        }
    }

    @Nested
    class ToResponse {
        @Test
        void shouldMapOnlyAllowedFields_FromLotEntity() {
            ZonedDateTime now = ZonedDateTime.now();
            UUID lotId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            LotEntity entity = new LotEntity(
                    lotId,
                    ownerId,
                    "Ferrari F40",
                    "Classic sport car",
                    BigDecimal.valueOf(100_000.00),
                    LotStatus.ACTIVE,
                    BigDecimal.valueOf(50.00),
                    now.plusDays(15),
                    now.minusDays(2),
                    now.minusDays(2)
            );

            LotResponse response = mapper.toResponse(entity);

            assertThat(response)
                    .isNotNull()
                    .extracting(
                            LotResponse::lotId, LotResponse::ownerId, LotResponse::name, LotResponse::description,
                            LotResponse::startPrice, LotResponse::status, LotResponse::minBidStep,
                            LotResponse::finishedAt, LotResponse::createdAt, LotResponse::updatedAt
                    )
                    .containsExactly(
                            lotId, ownerId, "Ferrari F40", "Classic sport car",
                            BigDecimal.valueOf(100_000.00), LotStatus.ACTIVE, BigDecimal.valueOf(50.00),
                            now.plusDays(15), now.minusDays(2), now.minusDays(2)
                    );
        }
    }

    @Nested
    class toListItemResponse {
        @Test
        void shouldMapOnlyAllowedFields_FromLotEntity() {
            ZonedDateTime now = ZonedDateTime.now();
            UUID lotId = UUID.randomUUID();
            UUID ownerId = UUID.randomUUID();
            LotEntity entity = new LotEntity(
                    lotId,
                    ownerId,
                    "Ferrari F40",
                    "Classic sport car",
                    BigDecimal.valueOf(100_000.00),
                    LotStatus.ACTIVE,
                    BigDecimal.valueOf(50.00),
                    now.plusDays(15),
                    now.minusDays(2),
                    now.minusDays(2)
            );

            LotListItemResponse response = mapper.toListItemResponse(entity);

            assertThat(response)
                    .isNotNull()
                    .extracting(
                            LotListItemResponse::lotId, LotListItemResponse::name, LotListItemResponse::startPrice
                    )
                    .containsExactly(
                            lotId, "Ferrari F40", BigDecimal.valueOf(100_000.00)
                    );
        }
    }
}
