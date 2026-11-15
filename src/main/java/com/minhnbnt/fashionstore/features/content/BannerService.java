package com.minhnbnt.fashionstore.features.content;

import com.minhnbnt.fashionstore.dto.content.BannerRequest;
import com.minhnbnt.fashionstore.dto.content.BannerResponse;
import com.minhnbnt.fashionstore.dto.content.PublicBannerResponse;
import com.minhnbnt.fashionstore.features.content.repository.SiteBannerRepository;
import com.minhnbnt.fashionstore.features.upload.S3UrlService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.model.BannerPlacement;
import com.minhnbnt.fashionstore.model.SiteBanner;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BannerService {

    private final SiteBannerRepository banners;

    private final UploadRepository uploads;

    private final S3UrlService s3Urls;

    private final Clock clock;

    @Transactional(readOnly = true)
    public List<BannerResponse> listForAdmin(@Nullable BannerPlacement placement) {
        var now = clock.instant();

        List<SiteBanner> found;
        if (placement == null) {
            found = banners.findAllForAdmin();
        } else {
            found = banners.findByPlacementForAdmin(placement.name());
        }

        return found.stream()
            .map(banner -> BannerResponse.of(
                banner,
                requiredImageUrl(banner.getImageUploadId()),
                optionalImageUrl(banner.getMobileImageUploadId()),
                now))
            .toList();
    }

    @Transactional
    public BannerResponse create(@NonNull UUID adminId, @NonNull BannerRequest request) {
        validateImageUpload(request.imageUploadId(), "Banner image");
        validateImageUpload(request.mobileImageUploadId(), "Banner mobile image");

        var banner = SiteBanner.create(
            request.placement(),
            request.title(),
            request.imageUploadId(),
            request.mobileImageUploadId(),
            request.linkUrl(),
            request.sortOrder(),
            request.active(),
            request.startsAt(),
            request.endsAt(),
            adminId
        );

        var saved = banners.save(banner);
        return BannerResponse.of(
            saved,
            requiredImageUrl(saved.getImageUploadId()),
            optionalImageUrl(saved.getMobileImageUploadId()),
            clock.instant());
    }

    @Transactional
    public BannerResponse update(@NonNull UUID adminId, @NonNull UUID bannerId, @NonNull BannerRequest request) {
        var banner = requireBanner(bannerId);

        validateImageUpload(request.imageUploadId(), "Banner image");
        validateImageUpload(request.mobileImageUploadId(), "Banner mobile image");

        banner.update(
            request.placement(),
            request.title(),
            request.imageUploadId(),
            request.mobileImageUploadId(),
            request.linkUrl(),
            request.sortOrder(),
            request.active(),
            request.startsAt(),
            request.endsAt(),
            adminId
        );

        var saved = banners.save(banner);
        return BannerResponse.of(
            saved,
            requiredImageUrl(saved.getImageUploadId()),
            optionalImageUrl(saved.getMobileImageUploadId()),
            clock.instant());
    }

    @Transactional
    public void delete(@NonNull UUID bannerId) {
        var banner = requireBanner(bannerId);
        banners.delete(banner);
    }

    @Transactional(readOnly = true)
    public List<PublicBannerResponse> running(@NonNull BannerPlacement placement) {
        return banners.findRunning(placement.name(), clock.instant()).stream()
            .map(banner -> PublicBannerResponse.of(
                banner,
                requiredImageUrl(banner.getImageUploadId()),
                optionalImageUrl(banner.getMobileImageUploadId())))
            .toList();
    }

    /**
     * Chỉ nhận ảnh trình duyệt render được. Bỏ {@code image/svg+xml}: bucket public read,
     * mở thẳng URL thì script trong file chạy được (cùng lý do với avatar, ảnh sản phẩm).
     */
    private static boolean isSupportedImage(String contentType) {

        return contentType.startsWith("image/") && !contentType.equals("image/svg+xml");
    }

    private void validateImageUpload(@Nullable UUID uploadId, String purpose) {

        if (uploadId == null) {
            return;
        }
        var upload = uploads.findById(uploadId).orElse(null);
        if (upload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, purpose + " upload not found");
        }
        if (!isSupportedImage(upload.getContentType())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, purpose + " upload must be a supported image");
        }
    }

    /** FK + validate phía request bảo đảm upload tồn tại; thiếu là lỗi dữ liệu, fail loud. */
    private String requiredImageUrl(UUID uploadId) {

        return s3Urls.publicUrl(uploads.findById(uploadId).orElseThrow().getS3Key());
    }

    private @Nullable String optionalImageUrl(@Nullable UUID uploadId) {

        if (uploadId == null) {
            return null;
        }
        return requiredImageUrl(uploadId);
    }

    private SiteBanner requireBanner(UUID bannerId) {
        var banner = banners.findById(bannerId).orElse(null);
        if (banner == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Banner not found");
        }
        return banner;
    }
}
