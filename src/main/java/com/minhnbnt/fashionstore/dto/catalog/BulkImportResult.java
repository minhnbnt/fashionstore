package com.minhnbnt.fashionstore.dto.catalog;

import java.util.List;

public record BulkImportResult(
    int successCount,
    int failedCount,
    List<String> errors
) {
}
