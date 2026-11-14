package com.minhnbnt.fashionstore.features.content;

import com.minhnbnt.fashionstore.dto.content.PopupRequest;
import com.minhnbnt.fashionstore.dto.content.PopupResponse;
import com.minhnbnt.fashionstore.dto.content.PublicPopupResponse;
import com.minhnbnt.fashionstore.features.content.repository.SitePopupRepository;
import com.minhnbnt.fashionstore.features.upload.S3UrlService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.model.SitePopup;
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
public class PopupService {

    private final SitePopupRepository popups;

    private final UploadRepository uploads;

    private final S3UrlService s3Urls;

    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PopupResponse> listForAdmin() {
        var now = clock.instant();

        return popups.findAllForAdmin().stream()
            .map(popup -> PopupResponse.of(popup, optionalImageUrl(popup.getImageUploadId()), now))
            .toList();
    }

    @Transactional
    public PopupResponse create(@NonNull UUID adminId, @NonNull PopupRequest request) {
        validateImageUpload(request.imageUploadId());

        var popup = SitePopup.create(
            request.name(),
            request.title(),
            request.body(),
            request.imageUploadId(),
            request.ctaLabel(),
            request.linkUrl(),
            request.audience(),
            request.frequency(),
            request.delaySeconds(),
            request.priority(),
            request.active(),
            request.startsAt(),
            request.endsAt(),
            adminId
        );

        var saved = popups.save(popup);
        return PopupResponse.of(saved, optionalImageUrl(saved.getImageUploadId()), clock.instant());
    }

    @Transactional
    public PopupResponse update(@NonNull UUID adminId, @NonNull UUID popupId, @NonNull PopupRequest request) {
        var popup = requirePopup(popupId);

        validateImageUpload(request.imageUploadId());

        popup.update(
            request.name(),
            request.title(),
            request.body(),
            request.imageUploadId(),
            request.ctaLabel(),
            request.linkUrl(),
            request.audience(),
            request.frequency(),
            request.delaySeconds(),
            request.priority(),
            request.active(),
            request.startsAt(),
            request.endsAt(),
            adminId
        );

        var saved = popups.save(popup);
        return PopupResponse.of(saved, optionalImageUrl(saved.getImageUploadId()), clock.instant());
    }

    @Transactional
    public void delete(@NonNull UUID popupId) {
        var popup = requirePopup(popupId);
        popups.delete(popup);
    }

    @Transactional(readOnly = true)
    public List<PublicPopupResponse> running() {
        return popups.findRunning(clock.instant()).stream()
            .map(popup -> PublicPopupResponse.of(popup, optionalImageUrl(popup.getImageUploadId())))
            .toList();
    }

    /**
     * Chỉ nhận ảnh trình duyệt render được. Bỏ {@code image/svg+xml}: bucket public read,
     * mở thẳng URL thì script trong file chạy được (cùng lý do với avatar, ảnh sản phẩm).
     */
    private static boolean isSupportedImage(String contentType) {

        return contentType.startsWith("image/") && !contentType.equals("image/svg+xml");
    }

    private void validateImageUpload(@Nullable UUID uploadId) {

        if (uploadId == null) {
            return;
        }
        var upload = uploads.findById(uploadId).orElse(null);
        if (upload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Popup image upload not found");
        }
        if (!isSupportedImage(upload.getContentType())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Popup image upload must be a supported image");
        }
    }

    private @Nullable String optionalImageUrl(@Nullable UUID uploadId) {

        if (uploadId == null) {
            return null;
        }
        // FK + validate phía request bảo đảm upload tồn tại; thiếu là lỗi dữ liệu, fail loud.
        return s3Urls.publicUrl(uploads.findById(uploadId).orElseThrow().getS3Key());
    }

    private SitePopup requirePopup(UUID popupId) {
        var popup = popups.findById(popupId).orElse(null);
        if (popup == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Popup not found");
        }
        return popup;
    }
}
