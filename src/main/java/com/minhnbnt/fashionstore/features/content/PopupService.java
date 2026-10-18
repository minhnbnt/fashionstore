package com.minhnbnt.fashionstore.features.content;

import com.minhnbnt.fashionstore.dto.content.PopupRequest;
import com.minhnbnt.fashionstore.dto.content.PopupResponse;
import com.minhnbnt.fashionstore.dto.content.PublicPopupResponse;
import com.minhnbnt.fashionstore.features.content.repository.SitePopupRepository;
import com.minhnbnt.fashionstore.model.SitePopup;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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

    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PopupResponse> listForAdmin() {
        var now = clock.instant();

        return popups.findAllForAdmin().stream()
            .map(popup -> PopupResponse.of(popup, now))
            .toList();
    }

    @Transactional
    public PopupResponse create(@NonNull UUID adminId, @NonNull PopupRequest request) {
        var popup = SitePopup.create(
            request.name(),
            request.title(),
            request.body(),
            request.imageKey(),
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

        return PopupResponse.of(popups.save(popup), clock.instant());
    }

    @Transactional
    public PopupResponse update(@NonNull UUID adminId, @NonNull UUID popupId, @NonNull PopupRequest request) {
        var popup = requirePopup(popupId);

        popup.update(
            request.name(),
            request.title(),
            request.body(),
            request.imageKey(),
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

        return PopupResponse.of(popups.save(popup), clock.instant());
    }

    @Transactional
    public void delete(@NonNull UUID popupId) {
        var popup = requirePopup(popupId);
        popups.delete(popup);
    }

    @Transactional(readOnly = true)
    public List<PublicPopupResponse> running() {
        return popups.findRunning(clock.instant()).stream()
            .map(PublicPopupResponse::of)
            .toList();
    }

    private SitePopup requirePopup(UUID popupId) {
        var popup = popups.findById(popupId).orElse(null);
        if (popup == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Popup not found");
        }
        return popup;
    }
}
