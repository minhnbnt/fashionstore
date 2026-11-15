package com.minhnbnt.fashionstore.infrastructure.seed;

import java.util.Optional;

/**
 * Tải nội dung ảnh demo. Tách interface để test không phụ thuộc mạng.
 */
@FunctionalInterface
public interface DemoImageFetcher {

    /**
     * Trả nội dung ảnh, hoặc rỗng khi không tải được (ảnh đó bị bỏ qua).
     */
    Optional<byte[]> fetch(String url);
}
