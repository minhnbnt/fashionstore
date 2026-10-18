package com.minhnbnt.fashionstore.dto.content;

/**
 * Link banner/popup: đường dẫn nội bộ ({@code /search?q=ao}, không nhận {@code //host}) hoặc URL http(s).
 * Chặn {@code javascript:} và scheme lạ vì frontend gắn thẳng vào {@code href}.
 */
public final class ContentLinks {

    public static final String PATTERN = "^(/(?!/)\\S*|https?://\\S+)$";

    public static final String MESSAGE = "must be a path starting with / or an http(s) URL";

    private ContentLinks() {
    }
}
