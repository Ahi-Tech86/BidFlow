package com.ahicode.bidflow.gateway;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties("application.gateway")
public class SecurityProperties {
    private List<String> publicEndpoints;
}
