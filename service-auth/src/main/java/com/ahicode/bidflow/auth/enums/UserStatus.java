package com.ahicode.bidflow.auth.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(enumAsRef = true, description = "Possible user account statuses")
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    BLOCKED
}
