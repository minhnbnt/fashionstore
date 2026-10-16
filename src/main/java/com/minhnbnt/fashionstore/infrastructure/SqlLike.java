package com.minhnbnt.fashionstore.infrastructure;

/**
 * Pattern cho ILIKE "chứa chuỗi". Postgres LIKE mặc định escape bằng '\', nên % và _ người dùng gõ
 * được hiểu là ký tự thường, không thành wildcard.
 */
public final class SqlLike {

    private SqlLike() {
    }

    public static String contains(String raw) {
        var escaped = raw.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
