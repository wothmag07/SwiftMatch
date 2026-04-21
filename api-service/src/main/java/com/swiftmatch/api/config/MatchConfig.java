package com.swiftmatch.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Matcher tuning — radius-expansion sequence and total budget.
 * Radius expansion sequence and the overall 8 s time budget.
 */
@ConfigurationProperties("swiftmatch.match")
public record MatchConfig(List<Integer> radiiKm, long timeoutMs) {

    public MatchConfig {
        if (radiiKm == null || radiiKm.isEmpty()) radiiKm = List.of(2, 5, 10);
        if (timeoutMs <= 0) timeoutMs = 8000L;
    }
}
