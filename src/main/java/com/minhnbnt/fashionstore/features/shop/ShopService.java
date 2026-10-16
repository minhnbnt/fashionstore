package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.dto.shop.ShopResponse;
import com.minhnbnt.fashionstore.dto.shop.UpdateShopProfileRequest;
import com.minhnbnt.fashionstore.dto.shop.UpdateShopRequest;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.Shop;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopStatus;
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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopRepository shops;

    private final ShopMemberRepository members;

    private final ShopFollowerRepository followers;

    private final ShopAccessChecker access;

    @Transactional
    public ShopResponse register(@NonNull UUID ownerId, @NonNull CreateShopRequest request) {
        // Fraud prevention check 1: CCCD format
        if (request.idNumber() != null && !request.idNumber().isBlank()) {
            var cleanedId = request.idNumber().trim();
            if (!cleanedId.matches("^\\d{9}$|^\\d{12}$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số CCCD/CMND không đúng định dạng (9 hoặc 12 chữ số).");
            }
        }

        // Fraud prevention check 2: Bank holder name MUST match legal name
        if (request.bankHolder() != null && request.legalName() != null
            && !request.bankHolder().isBlank() && !request.legalName().isBlank()) {
            var normHolder = normalizeName(request.bankHolder());
            var normLegal = normalizeName(request.legalName());
            if (!normHolder.equalsIgnoreCase(normLegal)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên chủ tài khoản ngân hàng phải trùng khớp với Họ và tên trên Căn cước công dân để chống gian lận.");
            }
        }

        var shop = Shop.create(
            request.name(),
            request.slug(),
            descriptionOf(request.description()),
            request.logoKey(),
            request.bannerKey(),
            request.phone(),
            request.address(),
            request.sellerType(),
            request.legalName(),
            request.idNumber(),
            request.idIssuedDate(),
            request.idIssuedPlace(),
            request.taxCode(),
            request.idFrontKey(),
            request.idBackKey(),
            request.idSelfieKey(),
            request.businessCertKey(),
            request.bankName(),
            request.bankAccount(),
            request.bankHolder()
        );
        try {
            shops.save(shop);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already taken");
        }
        members.add(ShopMember.owner(shop.getId(), ownerId));
        return toResponse(shop);
    }

    @Transactional
    public ShopResponse approve(@NonNull UUID adminId, @NonNull UUID id) {
        var shop = shops.findById(id).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        shop.approve(adminId);
        return toResponse(shops.save(shop));
    }

    @Transactional
    public ShopResponse reject(@NonNull UUID adminId, @NonNull UUID id) {
        return reject(adminId, id, null);
    }

    @Transactional
    public ShopResponse reject(@NonNull UUID adminId, @NonNull UUID id, @Nullable String reason) {
        var shop = shops.findById(id).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        shop.reject(reason);
        return toResponse(shops.save(shop));
    }

    @Transactional
    public ShopResponse suspend(@NonNull UUID adminId, @NonNull UUID id) {
        var shop = shops.findById(id).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        shop.suspend();
        return toResponse(shops.save(shop));
    }

    @Transactional
    public ShopResponse unsuspend(@NonNull UUID adminId, @NonNull UUID id) {
        var shop = shops.findById(id).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        shop.unsuspend();
        return toResponse(shops.save(shop));
    }

    @Transactional
    public ShopResponse update(@NonNull UUID userId, @NonNull UUID id, @NonNull UpdateShopRequest request) {
        var shop = shops.findById(id).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
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
        if (shop == null || shop.getStatus() != ShopStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }
        return toResponse(shop);
    }

    @Transactional(readOnly = true)
    public List<ShopResponse> listByStatus(@NonNull ShopStatus status) {
        return shops.findByStatusOrderByCreatedAtDesc(status).stream()
            .map(ShopService::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public boolean isShopApproved(@NonNull UUID shopId) {
        var shop = shops.findById(shopId).orElse(null);
        if (shop == null) {
            return false;
        }
        return shop.getStatus() == ShopStatus.APPROVED;
    }

    private static String descriptionOf(@Nullable String description) {
        if (description == null) {
            return "";
        }
        return description;
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

    public static ShopResponse toResponse(Shop shop) {
        return new ShopResponse(
            shop.getId(),
            shop.getName(),
            shop.getSlug(),
            shop.getDescription(),
            shop.getLogoKey(),
            shop.getBannerKey(),
            shop.getStatus().name(),
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
            shop.getCreatedAt()
        );
    }
}
