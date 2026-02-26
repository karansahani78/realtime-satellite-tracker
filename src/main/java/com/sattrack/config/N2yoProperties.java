package com.sattrack.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "n2yo")
@Getter
@Setter
public class N2yoProperties {
    private String apiKey;
}