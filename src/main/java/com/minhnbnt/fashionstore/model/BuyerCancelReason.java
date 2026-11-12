package com.minhnbnt.fashionstore.model;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Standard cancel reasons selectable by buyers when cancelling a pending order.
 */
public enum BuyerCancelReason {
    CHANGE_MIND("Đổi ý"),
    FOUND_CHEAPER("Tìm được giá rẻ hơn"),
    ORDERED_BY_MISTAKE("Đặt nhầm"),
    OTHER("Khác");

    private final String label;

    BuyerCancelReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static BuyerCancelReason parse(String raw) {
        var normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancel reason is required");
        }
        for (var value : values()) {
            if (value.name().equals(normalized)) {
                return value;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid buyer cancel reason: " + raw);
    }
}

