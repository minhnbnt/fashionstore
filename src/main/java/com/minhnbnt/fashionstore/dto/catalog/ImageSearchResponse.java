package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ImageSearchResponse(@Schema List<ImageSearchHit> items) {
}
