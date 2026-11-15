package com.minhnbnt.fashionstore.features.catalog.search;

import com.minhnbnt.fashionstore.dto.catalog.ImageSearchHit;
import com.minhnbnt.fashionstore.dto.catalog.ImageSearchResponse;
import com.minhnbnt.fashionstore.features.upload.S3UrlService;
import com.minhnbnt.fashionstore.infrastructure.EmbeddingClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

/** Guard nội bộ độc lập với controller để test được không cần HTTP. */
@Service
@RequiredArgsConstructor
public class ImageSearchService {

    public static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int OVER_FETCH = 3;

    private final EmbeddingClient client;
    private final ImageSearchRepository repository;
    private final S3UrlService s3Urls;

    public ImageSearchResponse search(byte[] image, int limit) {
        if (image.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ảnh rỗng");
        }
        if (image.length > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "ảnh quá lớn");
        }

        var vector = client.embed(image);
        var seen = new HashSet<UUID>();
        var items = new ArrayList<ImageSearchHit>(limit);
        for (var hit : repository.search(vector, limit * OVER_FETCH)) {
            if (!seen.add(hit.productId())) {
                continue;
            }
            items.add(new ImageSearchHit(
                hit.productId(),
                hit.name(),
                hit.slug(),
                hit.minPrice(),
                hit.defaultImageKey() == null ? null : s3Urls.publicUrl(hit.defaultImageKey()),
                1 - hit.distance()));
            if (items.size() == limit) {
                break;
            }
        }
        return new ImageSearchResponse(items);
    }
}
