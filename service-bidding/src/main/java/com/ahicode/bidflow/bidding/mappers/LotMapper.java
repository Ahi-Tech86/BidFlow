package com.ahicode.bidflow.bidding.mappers;

import com.ahicode.bidflow.bidding.dtos.*;
import com.ahicode.bidflow.bidding.entities.LotEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface LotMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "currentMaxBid", ignore = true)
    @Mapping(target = "highestBidderId", ignore = true)
    LotEntity toEntity(CreateLotRequest request);

    @Mapping(target = "lotId", source = "entity.id")
    @Mapping(target = "ownerId", source = "entity.ownerId")
    LotResponse toResponse(LotEntity entity);

    @Mapping(target = "lotId", source = "entity.id")
    LotListItemResponse toListItemResponse(LotEntity entity);

    default LotDetailsResponse toDetailsResponse(LotResponse lotResponse, CurrentBidInfo bidInfo) {
        return new LotDetailsResponse(
                lotResponse.lotId(),
                lotResponse.ownerId(),
                lotResponse.name(),
                lotResponse.description(),
                lotResponse.startPrice(),
                lotResponse.minBidStep(),
                bidInfo.currentMaxBid(),
                bidInfo.highestBidderId(),
                lotResponse.status(),
                lotResponse.finishedAt(),
                lotResponse.createdAt(),
                lotResponse.updatedAt()
        );
    }
}
