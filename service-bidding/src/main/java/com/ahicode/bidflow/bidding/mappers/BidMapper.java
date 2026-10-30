package com.ahicode.bidflow.bidding.mappers;

import com.ahicode.bidflow.bidding.dtos.BidRequest;
import com.ahicode.bidflow.bidding.dtos.BidResponse;
import com.ahicode.bidflow.bidding.entities.BidEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface BidMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "lotId", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    BidEntity toEntity(BidRequest request);

    BidResponse toResponse(BidEntity entity);
}
