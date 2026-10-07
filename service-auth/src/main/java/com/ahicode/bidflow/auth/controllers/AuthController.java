package com.ahicode.bidflow.auth.controllers;

import com.ahicode.bidflow.auth.dtos.*;
import com.ahicode.bidflow.auth.exceptions.InvalidTokenException;
import com.ahicode.bidflow.auth.services.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User registration and JWT token management")
public class AuthController {

    private final AuthService service;

    @PostMapping("/register")
    @Operation(
            summary = "Register a new user account",
            description = "Creates a new user account with email, password, and personal information." +
                    " Returns JWT tokens on success."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "User successfully created and authenticated",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed: invalid email format, weak password or missing required fields",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict: A user with this email address already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<AuthResponse> register(@RequestBody @Valid RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request));
    }

    @PostMapping("/login")
    @Operation(
            summary = "Authenticate user and receive tokens",
            description = "Authenticates a user with email and password. Returns an access token for API authentication " +
                    "and refresh token for obtaining new access tokens without re-login"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully authentication",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request body (e.g. missing email or password)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Authentication failed: incorrect email or password",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Account is blocked or suspended",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(service.login(request));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh access token using a valid refresh token",
            description = "Issues a new Access Token using the provided Refresh Token."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Token successfully refreshed",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized: Missing 'Bearer' prefix, invalid token format, or expired refresh token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: The user account associated with the token is blocked or suspended",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<AuthResponse> refresh(
            @Parameter(
                    description = "Authorization header containing the Refresh Token. Format: 'Bearer'",
                    required = true,
                    example = "Bearer eyJhbGciOiJIUzI1NiJ9..."
            )
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Missing or invalid Authorization header");
        }

        String refreshToken = authHeader.substring(7);

        return ResponseEntity.ok(service.refreshAccessToken(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Logout user and invalidate tokens",
            description = "Invalidates the current Access Token and Refresh Token, effectively ending the user session" +
                    "The Access Token is taken from Authorization header, while the Refresh Token is provided in the " +
                    "request body."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Successfully logged out. Tokens have been invalidated."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: Invalid refresh token format or missing required fields",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized: Missing or invalid Access Token in Authorization header",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    public ResponseEntity<Void> logout(
            @Parameter(
                    description = "Authorization header containing the current Access Token. Format: 'Bearer <token>'",
                    example = "Bearer eyJhbGciOiJIUzI1NiJ9...",
                    required = true
            )
            @RequestHeader(value = "Authorization", required = false) String authHeader,

            @Parameter(description = "Request body containing the Refresh Token to be invalidated")
            @RequestBody @Valid LogoutRequest request
    ) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Missing or invalid Authorization header");
        }

        String accessToken = authHeader.substring(7);
        String refreshToken = request.refreshToken();
        service.logout(accessToken, refreshToken);

        return ResponseEntity.noContent().build();
    }
}
