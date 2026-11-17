package com.ahicode.bidflow.bidding.controllers;

import com.ahicode.bidflow.bidding.dtos.BidHistoryResponse;
import com.ahicode.bidflow.bidding.dtos.BidRequest;
import com.ahicode.bidflow.bidding.dtos.BidResponse;
import com.ahicode.bidflow.bidding.dtos.PageResponse;
import com.ahicode.bidflow.bidding.services.BidService;
import com.ahicode.bidflow.common.dtos.ErrorResponse;
import com.ahicode.bidflow.common.enums.UserStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lots")
@Tag(name = "Bidding", description = "Endpoints for managing bids")
public class BidController {

    private static final String GATEWAY_HEADER_DESC = "Resolved by API Gateway from the validated JWT. " +
            "Direct calls bypassing the gateway are not supported.";

    private final BidService service;

    @PostMapping("/{lotId}/bids")
    @Operation(
            summary = "Place a bid on a lot",
            description = "Places a new bid on existing auction lot. The bid must be meet the minimum requirements " +
                    "(start price or current max bid + minimum step)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Created: Bid placed successfully",
                    content = @Content(schema = @Schema(implementation = BidResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: " +
                            "1) Invalid input data (e.g., non-positive bid value)" +
                            "2) Attempting to bid on an inactive, closed, or cancelled lot." +
                            "3) Bid value is too low (less than starting price OR less than the current highest bid + minimum bid step).",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: " +
                            "1) The user account is inactive or blocked (based on X-User-Status)" +
                            "2) The owner of the lot is attempting to place a bid on their own lot.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Not Found: The lot with the specified ID does not exist.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<BidResponse> makeBid(
            @Parameter(
                    description = GATEWAY_HEADER_DESC,
                    required = true,
                    example = "123e4567-e89b-12d3-a456-426614174000"
            )
            @RequestHeader("X-User-Id") UUID userId,

            @Parameter(
                    description = GATEWAY_HEADER_DESC,
                    schema = @Schema(implementation = UserStatus.class),
                    required = true,
                    example = "ACTIVE"
            )
            @RequestHeader("X-User-Status") UserStatus userStatus,

            @Parameter(description = "Unique identifier of the lot to bid on", required = true)
            @PathVariable("lotId") UUID lotId,

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "The bid details including the proposed bid value",
                    required = true
            )
            @RequestBody @Valid BidRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.placeBid(request, lotId, userId, userStatus));
    }

    @GetMapping("/{lotId}/bids")
    @Operation(
            summary = "Get bid history for a lot",
            description = "Retrieves the paginated history of all bids placed on specific auction lot." +
                    "Bids are typically sorted by creation time in descending order (newest first)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "OK: Bid history retrieved successfully",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: Invalid lot ID format or pagination parameters.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<PageResponse<BidHistoryResponse>> getBidsHistory(
            @Parameter(description = "Unique identifier of the lot", required = true)
            @PathVariable("lotId") UUID lotId,

            @Parameter(description = "Pagination parameters (page, size, sort)")
            Pageable pageable
    ) {
        return ResponseEntity.ok(service.getBidsHistory(lotId, pageable));
    }
}
