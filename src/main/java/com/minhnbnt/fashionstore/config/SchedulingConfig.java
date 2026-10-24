package com.minhnbnt.fashionstore.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật {@code @Scheduled} (đồng bộ lịch lễ định kỳ).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
