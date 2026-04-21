package com.swiftmatch.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Single injectable {@link Clock} so all server-side {@code now()}
 * calls go through this bean so tests can substitute a fixed/offset clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
