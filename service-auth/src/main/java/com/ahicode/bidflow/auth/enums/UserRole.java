package com.ahicode.bidflow.auth.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true, description = "Possible user roles")
public enum UserRole {
    ROLE_USER,
    ROLE_MODERATOR,
    ROLE_ADMIN
}
