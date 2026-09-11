package com.ahicode.bidflow.auth.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.security.core.GrantedAuthority;

@Schema(enumAsRef = true, description = "Possible user roles")
public enum UserRole implements GrantedAuthority {
    ROLE_USER,
    ROLE_MODERATOR,
    ROLE_ADMIN;

    @Override
    public String getAuthority() {
        return name();
    }
}
