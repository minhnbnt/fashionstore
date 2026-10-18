package com.minhnbnt.fashionstore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.LocalDate;
import java.util.List;

/**
 * Ngày nghỉ lễ không tính vào SLA xử lý đơn (Chủ nhật luôn được bỏ qua, không cần khai).
 * Lịch lễ do Chính phủ chốt từng năm nên khai trong {@code application.yml}, cập nhật mỗi năm.
 */
@ConfigurationProperties(prefix = "order-sla")
public record OrderSlaProperties(@DefaultValue List<LocalDate> holidays) {
}
