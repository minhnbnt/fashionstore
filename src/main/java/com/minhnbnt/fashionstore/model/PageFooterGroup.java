package com.minhnbnt.fashionstore.model;

/**
 * Cột footer chứa link trang tĩnh. Tên trùng CHECK constraint {@code site_page.footer_group}.
 */
public enum PageFooterGroup {
    /** "Về nét". */
    ABOUT,
    /** "Chính sách". */
    POLICY,
    /** "Hỗ trợ". */
    SUPPORT,
    /** Không hiện ở footer, chỉ vào bằng link. */
    NONE
}
