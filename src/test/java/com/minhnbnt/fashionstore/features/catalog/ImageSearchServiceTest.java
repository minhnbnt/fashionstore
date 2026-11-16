package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.dto.catalog.ImageSearchHit;
import com.minhnbnt.fashionstore.features.catalog.search.ImageSearchRepository;
import com.minhnbnt.fashionstore.features.catalog.search.ImageSearchService;
import com.minhnbnt.fashionstore.features.upload.S3UrlService;
import com.minhnbnt.fashionstore.infrastructure.EmbeddingClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.jspecify.annotations.Nullable;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageSearchServiceTest {

    private static final byte[] IMAGE = new byte[]{1, 2, 3};
    private static final UUID SHOP_ID = UUID.randomUUID();

    @Mock
    private EmbeddingClient client;
    @Mock
    private ImageSearchRepository repository;
    @Mock
    private S3UrlService s3Urls;

    private ImageSearchService service;

    @BeforeEach
    void setUp() {
        service = new ImageSearchService(client, repository, s3Urls);
    }

    private static ImageSearchRepository.Scored scored(
        UUID productId, double distance, @Nullable String imageKey) {
        return new ImageSearchRepository.Scored(
            productId, SHOP_ID, "Áo", "ao", 250_000L, imageKey, distance);
    }

    @Test
    void dedupesByProductKeepingFirstRowWhichIsMinDistance() {
        // Repository ORDER BY distance: row đầu tiên của mỗi product là distance nhỏ nhất.
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var vector = new float[512];
        when(client.embed(IMAGE)).thenReturn(vector);
        when(repository.search(vector, 30)).thenReturn(List.of(
            scored(b, 0.1, null),
            scored(a, 0.2, "a.png"),
            scored(a, 0.4, "a.png"),
            scored(a, 0.9, "a.png")
        ));

        var result = service.search(IMAGE, 10);

        assertThat(result.items()).extracting(ImageSearchHit::id).containsExactly(b, a);
        assertThat(result.items().get(0).similarity()).isCloseTo(0.9, offset(1e-9));
        assertThat(result.items().get(1).similarity()).isCloseTo(0.8, offset(1e-9));
        verify(repository).search(vector, 30);
    }

    @Test
    void stopsAtLimitAfterDedupe() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var c = UUID.randomUUID();
        var vector = new float[512];
        when(client.embed(IMAGE)).thenReturn(vector);
        when(repository.search(vector, 6)).thenReturn(List.of(
            scored(a, 0.2, null),
            scored(a, 0.35, null),
            scored(b, 0.4, null),
            scored(c, 0.9, null)
        ));

        var result = service.search(IMAGE, 2);

        assertThat(result.items()).extracting(ImageSearchHit::id).containsExactly(a, b);
        verify(repository).search(vector, 6);
    }

    @Test
    void mapsImageKeyToPublicUrl() {
        var id = UUID.randomUUID();
        var vector = new float[512];
        when(client.embed(IMAGE)).thenReturn(vector);
        when(repository.search(vector, 60)).thenReturn(List.of(scored(id, 0.2, "products/a.png")));
        when(s3Urls.publicUrl("products/a.png")).thenReturn("https://cdn/a.png");

        var result = service.search(IMAGE, 20);

        assertThat(result.items().get(0).defaultImageUrl()).isEqualTo("https://cdn/a.png");
        assertThat(result.items().get(0).similarity()).isCloseTo(0.8, offset(1e-9));
    }

    @Test
    void rejectsEmptyImage() {
        assertThatThrownBy(() -> service.search(new byte[0], 10))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsOversizeImage() {
        assertThatThrownBy(() -> service.search(new byte[ImageSearchService.MAX_IMAGE_BYTES + 1], 10))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(e -> ((ResponseStatusException) e).getStatusCode())
            .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
