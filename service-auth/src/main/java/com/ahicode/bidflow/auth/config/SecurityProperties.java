package com.ahicode.bidflow.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "application.security")
public class SecurityProperties {
    private List<String> publicEndpoints;
}
