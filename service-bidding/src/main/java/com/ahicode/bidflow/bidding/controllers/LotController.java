package com.ahicode.bidflow.bidding.controllers;

import com.ahicode.bidflow.bidding.dtos.*;
import com.ahicode.bidflow.bidding.enums.LotStatus;
import com.ahicode.bidflow.bidding.services.LotService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lots")
@Tag(name = "Bidding", description = "Endpoints for managing auction lots and placing bids")
public class LotController {

    private static final String GATEWAY_HEADER_DESC = "Resolved by API Gateway from the validated JWT. " +
            "Direct calls bypassing the gateway are not supported.";

    private final LotService lotService;

    @GetMapping
    @Operation(
            summary = "Retrieves a paginated list of auction lots",
            description = "Returns a paginated list of auction lots, optionally filtered by status" +
                    "If no status is specified, it defaults to returning only ACTIVE lots." +
                    "Results are sorted by creation date in descending order."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ok: Successfully retrieved the paginated list of lots",
                    content = @Content(schema = @Schema(implementation = LotListItemResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: Invalid pagination parameters or invalid lot status value",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<Page<LotListItemResponse>> getLotsByStatus(
            @Parameter(description = "Filter by lot status. If omitted, defaults to ACTIVE")
            @RequestParam(value = "lotStatus", required = false) LotStatus lotStatus,

            @Parameter(description = "Pagination and sorting information")
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(lotService.getLotsByStatus(lotStatus, pageable));
    }

    @PostMapping
    @Operation(
            summary = "Creates a new auction lot",
            description = "Creates a new lot for an auction. " +
                    "The user specified in the headers will be  set as the lot owner."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Created: the lot created successfully.",
                    content = @Content(schema = @Schema(implementation = LotResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: Invalid request payload or validation errors (e.g., negative start price).",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: The user account is inactive, blocked, or lacks permission to create a lot.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<LotResponse> createLot(
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

            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Details of the lot to be created",
                    required = true
            )
            @RequestBody @Valid CreateLotRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(lotService.createLot(request, userId, userStatus));
    }

    @GetMapping("/{lotId}")
    @Operation(
            summary = "Retrieves detailed information about a specified auction lot",
            description = "Fetches comprehensive details of a single auction lot by its unique identifier"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ok: Successfully retrieved the lot details",
                    content = @Content(schema = @Schema(implementation = LotResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Not Found: The lot with the specified ID does not exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<LotResponse> getLotDetails(
            @Parameter(description = "Unique identifier of the auction lot", required = true)
            @PathVariable("lotId") UUID lotId
    ) {
        return ResponseEntity.ok(lotService.getLotDetails(lotId));
    }

    @PatchMapping("/{lotId}/cancel")
    @Operation(
            summary = "Cancels an active auction lot",
            description = "Cancels a specific auction lot. This operation can only be performed " +
                    "by the owner of the lot and the lot must be currently be in the ACTIVE status."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ok: Successfully cancelled the auction lot",
                    content = @Content(schema = @Schema(implementation = LotResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: The lot is not in ACTIVE status and cannot be cancelled",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: The user account is inactive/blocked, " +
                            "or the authenticated user is not the owner of the lot.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Not Found: The lot with the specified ID does not exist.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<LotResponse> cancelAuctionLot(
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

            @Parameter(description = "Unique identifier of the lot to cancel", required = true)
            @PathVariable("lotId") UUID lotId
    ) {
        return ResponseEntity.ok(lotService.cancelAuctionLot(lotId, userId, userStatus));
    }

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
                            "2) Attemptint to bid on an inactive, closed, or cancelled lot." +
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
        return ResponseEntity.status(HttpStatus.CREATED).body(lotService.makeBid(request, lotId, userId, userStatus));
    }
}
