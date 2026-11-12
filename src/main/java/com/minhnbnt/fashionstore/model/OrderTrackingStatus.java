package com.minhnbnt.fashionstore.model;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Valid tracking statuses that sellers can record on an order in transit.
 */
public enum OrderTrackingStatus {
    PICKED_UP("Đơn vị vận chuyển đã lấy hàng"),
    IN_TRANSIT("Đang trung chuyển"),
    SORTING_FACILITY("Đã đến kho phân loại"),
    OUT_FOR_DELIVERY("Đang giao hàng"),
    CUSTOM("Cập nhật vận chuyển");

    private final String label;

    OrderTrackingStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static OrderTrackingStatus parse(String raw) {
        var normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tracking status is required");
        }
        for (var value : values()) {
            if (value.name().equals(normalized)) {
                return value;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid tracking status: " + raw);
    }
}

