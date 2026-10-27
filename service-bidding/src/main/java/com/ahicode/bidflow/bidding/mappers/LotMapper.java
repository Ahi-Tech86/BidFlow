package com.ahicode.bidflow.bidding.mappers;

import com.ahicode.bidflow.bidding.dtos.CreateLotRequest;
import com.ahicode.bidflow.bidding.dtos.LotListItemResponse;
import com.ahicode.bidflow.bidding.dtos.LotResponse;
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
    LotEntity toEntity(CreateLotRequest request);

    @Mapping(target = "lotId", source = "entity.id")
    @Mapping(target = "ownerId", source = "entity.ownerId")
    LotResponse toResponse(LotEntity entity);

    @Mapping(target = "lotId", source = "entity.id")
    LotListItemResponse toListItemResponse(LotEntity entity);
}
