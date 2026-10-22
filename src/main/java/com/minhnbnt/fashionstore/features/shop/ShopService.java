package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.admin.AdminShopResponse;
import com.minhnbnt.fashionstore.dto.admin.StatusHistoryResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.dto.shop.ShopResponse;
import com.minhnbnt.fashionstore.dto.shop.UpdateShopProfileRequest;
import com.minhnbnt.fashionstore.dto.shop.UpdateShopRequest;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.Shop;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopStatus;
import com.minhnbnt.fashionstore.model.ShopStatusHistory;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shops;

    private final ShopMemberRepository members;

    private final ShopStatusHistoryRepository histories;

    private final ShopAdminQueryRepository adminQueries;

    private final ShopFollowerRepository followers;

    private final ShopAccessChecker access;

    @Transactional
    public ShopResponse register(@NonNull UUID ownerId, @NonNull CreateShopRequest request) {
        validateRequired(request.name(), "Tên gian hàng (name)");
        validateRequired(request.slug(), "Định danh gian hàng (slug)");
        validateRequired(request.description(), "Mô tả gian hàng (description)");
        validateRequired(request.logoKey(), "Logo gian hàng (logoKey)");
        validateRequired(request.bannerKey(), "Ảnh bìa gian hàng (bannerKey)");
        validateRequired(request.phone(), "Số điện thoại kho (phone)");
        validateRequired(request.address(), "Địa chỉ kho lấy hàng (address)");
        validateRequired(request.legalName(), "Họ và tên chủ shop (legalName)");
        validateRequired(request.idNumber(), "Số CCCD/CMND (idNumber)");
        validateRequired(request.idIssuedDate(), "Ngày cấp CCCD (idIssuedDate)");
        validateRequired(request.idIssuedPlace(), "Nơi cấp CCCD (idIssuedPlace)");
        validateRequired(request.idFrontKey(), "Ảnh mặt trước CCCD (idFrontKey)");
        validateRequired(request.idBackKey(), "Ảnh mặt sau CCCD (idBackKey)");
        validateRequired(request.idSelfieKey(), "Ảnh selfie cầm CCCD (idSelfieKey)");
        validateRequired(request.bankName(), "Tên ngân hàng (bankName)");
        validateRequired(request.bankAccount(), "Số tài khoản ngân hàng (bankAccount)");
        validateRequired(request.bankHolder(), "Tên chủ tài khoản ngân hàng (bankHolder)");

        // Fraud prevention check 1: CCCD format (9 or 12 digits)
        var cleanedId = request.idNumber().trim();
        if (!cleanedId.matches("^\\d{9}$|^\\d{12}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số CCCD/CMND không đúng định dạng (9 hoặc 12 chữ số).");
        }

        // Fraud prevention check 2: Bank holder name MUST match legal name
        var normHolder = normalizeName(request.bankHolder());
        var normLegal = normalizeName(request.legalName());
        if (!normHolder.equalsIgnoreCase(normLegal)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên chủ tài khoản ngân hàng phải trùng khớp với Họ và tên trên Căn cước công dân để chống gian lận.");
        }

        var shop = Shop.create(
            request.name().trim(),
            request.slug().trim(),
            request.description().trim(),
            request.logoKey().trim(),
            request.bannerKey().trim(),
            request.phone().trim(),
            request.address().trim(),
            (request.sellerType() != null && !request.sellerType().isBlank()) ? request.sellerType().trim() : "INDIVIDUAL",
            request.legalName().trim(),
            cleanedId,
            request.idIssuedDate().trim(),
            request.idIssuedPlace().trim(),
            request.taxCode() != null ? request.taxCode().trim() : null,
            request.idFrontKey().trim(),
            request.idBackKey().trim(),
            request.idSelfieKey().trim(),
            request.businessCertKey() != null ? request.businessCertKey().trim() : null,
            request.bankName().trim(),
            request.bankAccount().trim(),
            request.bankHolder().trim()
        );
        try {
            shops.save(shop);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already taken");
        }
        members.add(ShopMember.owner(shop.getId(), ownerId));
        histories.save(ShopStatusHistory.record(shop.getId(), null, shop.getStatus(), ownerId, ""));
        return toResponse(shop);
    }

    @Transactional
    public ShopResponse approve(@NonNull UUID adminId, @NonNull UUID id) {
        return transition(adminId, id, "", shop -> shop.approve(adminId));
    }

    @Transactional
    public ShopResponse reject(@NonNull UUID adminId, @NonNull UUID id, @NonNull String reason) {
        return transition(adminId, id, reason, shop -> shop.reject(reason));
    }

    @Transactional
    public ShopResponse reject(@NonNull UUID adminId, @NonNull UUID id) {
        return reject(adminId, id, "");
    }

    @Transactional
    public ShopResponse suspend(@NonNull UUID adminId, @NonNull UUID id, @NonNull String reason) {
        return transition(adminId, id, reason, shop -> shop.suspend(reason));
    }

    @Transactional
    public ShopResponse suspend(@NonNull UUID adminId, @NonNull UUID id) {
        return suspend(adminId, id, "");
    }

    @Transactional
    public ShopResponse reinstate(@NonNull UUID adminId, @NonNull UUID id) {
        return transition(adminId, id, "", Shop::reinstate);
    }

    @Transactional
    public ShopResponse unsuspend(@NonNull UUID adminId, @NonNull UUID id) {
        return reinstate(adminId, id);
    }

    @Transactional
    public ShopResponse setTrusted(@NonNull UUID adminId, @NonNull UUID id, boolean trusted) {
        return transition(adminId, id, "", shop -> shop.setTrusted(trusted));
    }

    @Transactional
    public ShopResponse resubmit(@NonNull UUID ownerId, @NonNull UUID id) {
        return transition(ownerId, id, "", shop -> {
            access.requireShopRole(ownerId, id, MemberRole.OWNER);
            shop.resubmit();
        });
    }

    /**
     * Tạm khóa mọi shop APPROVED mà user làm OWNER, dùng khi khóa tài khoản owner.
     */
    @Transactional
    public List<UUID> suspendOwnedShops(@NonNull UUID ownerId, @NonNull UUID adminId, @NonNull String reason) {
        var ids = adminQueries.findApprovedShopIdsOwnedBy(ownerId);
        for (var id : ids) {
            suspend(adminId, id, reason);
        }
        return ids;
    }

    @Transactional
    public ShopResponse update(@NonNull UUID userId, @NonNull UUID id, @NonNull UpdateShopRequest request) {
        var shop = lockedShop(id);
        access.requireShopRole(userId, id, MemberRole.OWNER, MemberRole.STAFF);

        shop.updateDetails(request.name(), request.description(), request.logoKey(), request.bannerKey());
        return toResponse(shops.save(shop));
    }

    @Transactional
    public ShopResponse updateProfile(@NonNull UUID userId, @NonNull UUID id, @NonNull UpdateShopProfileRequest request) {
        var shop = shops.findById(id).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        access.requireShopRole(userId, id, MemberRole.OWNER, MemberRole.STAFF);
        shop.updateProfile(
            request.phone(),
            request.address(),
            request.returnPolicy(),
            request.processingDays(),
            request.bankName(),
            request.bankAccount(),
            request.bankHolder(),
            request.autoReplyText(),
            request.workingHours()
        );
        return toResponse(shops.save(shop));
    }

    @Transactional
    public void follow(@NonNull UUID userId, @NonNull UUID shopId) {
        var shop = shops.findById(shopId).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        var inserted = followers.add(shopId, userId);
        if (inserted > 0) {
            shop.incrementFollowers();
            shops.save(shop);
        }
    }

    @Transactional
    public void unfollow(@NonNull UUID userId, @NonNull UUID shopId) {
        var shop = shops.findById(shopId).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        var deleted = followers.remove(shopId, userId);
        if (deleted > 0) {
            shop.decrementFollowers();
            shops.save(shop);
        }
    }

    @Transactional(readOnly = true)
    public List<ShopResponse> getMine(@NonNull UUID userId) {
        var ids = members.findShopIdsByUser(userId);
        return StreamSupport.stream(shops.findAllById(ids).spliterator(), false)
            .sorted(Comparator.comparing(Shop::getCreatedAt).reversed())
            .map(ShopService::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public ShopResponse getBySlug(@NonNull String slug) {
        var shop = shops.findBySlug(slug).orElse(null);
        if (shop == null) {
            try {
                var id = UUID.fromString(slug);
                shop = shops.findById(id).orElse(null);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (shop == null || shop.getStatus() != ShopStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        return toResponse(shop);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminShopResponse> searchForAdmin(
        @Nullable ShopStatus status,
        @Nullable String query,
        int page,
        int size
    ) {
        var items = adminQueries.search(status, query, page, size);
        var total = adminQueries.count(status, query);
        return new PageResponse<>(items, total, page, size);
    }

    @Transactional(readOnly = true)
    public AdminShopResponse getForAdmin(@NonNull UUID id) {
        var shop = adminQueries.findById(id).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        return shop;
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> historyOf(@NonNull UUID shopId) {
        return adminQueries.history(shopId);
    }

    @Transactional(readOnly = true)
    public Map<ShopStatus, Long> countByStatus() {
        return adminQueries.countByStatus();
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, Long> countCreatedPerDay(@NonNull Instant from, @NonNull ZoneId zone) {
        return adminQueries.countCreatedPerDay(from, zone);
    }

    @Transactional(readOnly = true)
    public @Nullable UUID ownerOf(@NonNull UUID shopId) {
        return members.findOwner(shopId).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean isMember(@NonNull UUID shopId, @NonNull UUID userId) {
        return members.findRole(shopId, userId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isShopApproved(@NonNull UUID shopId) {
        var shop = shops.findById(shopId).orElse(null);
        if (shop == null) {
            return false;
        }
        return shop.getStatus() == ShopStatus.APPROVED;
    }

    /**
     * Khóa dòng shop, đổi trạng thái, ghi lịch sử, tất cả trong 1 transaction.
     */
    private ShopResponse transition(UUID actorId, UUID id, String reason, Consumer<Shop> change) {
        var shop = lockedShop(id);
        var from = shop.getStatus();

        change.accept(shop);
        shops.save(shop);

        histories.save(ShopStatusHistory.record(id, from, shop.getStatus(), actorId, reason));
        return toResponse(shop);
    }

    private Shop lockedShop(UUID id) {
        var shop = shops.findLockedById(id).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        return shop;
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "")
            .replaceAll("[đĐ]", "d")
            .replaceAll("\\s+", " ")
            .toUpperCase(java.util.Locale.ROOT);
    }

    private static void validateRequired(@Nullable String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " là bắt buộc và không được để trống.");
        }
    }

    public static ShopResponse toResponse(Shop shop) {
        return new ShopResponse(
            shop.getId(),
            shop.getName(),
            shop.getSlug(),
            shop.getDescription(),
            shop.getLogoKey(),
            shop.getBannerKey(),
            shop.getStatus().name(),
            shop.getStatusReason(),
            shop.getPhone(),
            shop.getAddress(),
            shop.getReturnPolicy(),
            shop.getProcessingDays(),
            shop.getSellerType(),
            shop.getRating(),
            shop.getRatingCount(),
            shop.getFollowerCount(),
            shop.getBankName(),
            shop.getBankAccount(),
            shop.getBankHolder(),
            shop.getAutoReplyText(),
            shop.getWorkingHours(),
            shop.getLegalName(),
            shop.getIdNumber(),
            shop.getIdIssuedDate(),
            shop.getIdIssuedPlace(),
            shop.getTaxCode(),
            shop.getIdFrontKey(),
            shop.getIdBackKey(),
            shop.getIdSelfieKey(),
            shop.getBusinessCertKey(),
            shop.getKycStatus(),
            shop.getKycRejectionReason(),
            shop.isTrusted(),
            shop.getCreatedAt()
        );
    }
}
