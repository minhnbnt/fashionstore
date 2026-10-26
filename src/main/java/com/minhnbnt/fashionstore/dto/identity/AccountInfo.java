package com.minhnbnt.fashionstore.dto.identity;

import java.util.UUID;

/**
 * Tài khoản đã đăng ký trên website, dùng nội bộ để mời nhân viên
 * và hiển thị tên chủ shop.
 */
public record AccountInfo(
    UUID id,
    String email,
    String displayName
) {
}
