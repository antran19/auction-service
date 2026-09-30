package com.nexus.auction.api.mapper;

import com.nexus.auction.api.dto.response.AuctionResponse;
import com.nexus.auction.api.dto.response.BidResponse;
import com.nexus.auction.application.usecase.AuctionResult;
import com.nexus.auction.application.usecase.BidResult;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuctionApiMapper {
    AuctionResponse toResponse(AuctionResult result);
    BidResponse toResponse(BidResult result);
}
