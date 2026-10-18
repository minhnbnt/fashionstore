package com.minhnbnt.fashionstore.features.content;

import com.minhnbnt.fashionstore.dto.content.BannerRequest;
import com.minhnbnt.fashionstore.dto.content.BannerResponse;
import com.minhnbnt.fashionstore.dto.content.PublicBannerResponse;
import com.minhnbnt.fashionstore.features.content.repository.SiteBannerRepository;
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
            .map(banner -> BannerResponse.of(banner, now))
            .toList();
    }

    @Transactional
    public BannerResponse create(@NonNull UUID adminId, @NonNull BannerRequest request) {
        var banner = SiteBanner.create(
            request.placement(),
            request.title(),
            request.imageKey(),
            request.mobileImageKey(),
            request.linkUrl(),
            request.sortOrder(),
            request.active(),
            request.startsAt(),
            request.endsAt(),
            adminId
        );

        return BannerResponse.of(banners.save(banner), clock.instant());
    }

    @Transactional
    public BannerResponse update(@NonNull UUID adminId, @NonNull UUID bannerId, @NonNull BannerRequest request) {
        var banner = requireBanner(bannerId);

        banner.update(
            request.placement(),
            request.title(),
            request.imageKey(),
            request.mobileImageKey(),
            request.linkUrl(),
            request.sortOrder(),
            request.active(),
            request.startsAt(),
            request.endsAt(),
            adminId
        );

        return BannerResponse.of(banners.save(banner), clock.instant());
    }

    @Transactional
    public void delete(@NonNull UUID bannerId) {
        var banner = requireBanner(bannerId);
        banners.delete(banner);
    }

    @Transactional(readOnly = true)
    public List<PublicBannerResponse> running(@NonNull BannerPlacement placement) {
        return banners.findRunning(placement.name(), clock.instant()).stream()
            .map(PublicBannerResponse::of)
            .toList();
    }

    private SiteBanner requireBanner(UUID bannerId) {
        var banner = banners.findById(bannerId).orElse(null);
        if (banner == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Banner not found");
        }
        return banner;
    }
}
