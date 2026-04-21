package com.swiftmatch.indexer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activates {@code @Scheduled} methods (location_history batch flusher).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
