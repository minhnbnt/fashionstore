package com.minhnbnt.fashionstore.features.location;

import com.minhnbnt.fashionstore.model.Region;

/**
 * Tỉnh + xã đã được xác thực thuộc nhau, kèm miền của tỉnh.
 */
public record ResolvedLocation(
    String provinceCode,
    String provinceName,
    Region region,
    String wardCode,
    String wardName
) {
}
