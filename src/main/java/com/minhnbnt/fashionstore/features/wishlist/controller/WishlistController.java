package com.minhnbnt.fashionstore.features.wishlist.controller;

import com.minhnbnt.fashionstore.dto.wishlist.WishlistItemResponse;
import com.minhnbnt.fashionstore.dto.wishlist.WishlistToggleRequest;
import com.minhnbnt.fashionstore.dto.wishlist.WishlistToggleResponse;
import com.minhnbnt.fashionstore.features.wishlist.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
@Tag(name = "Wishlist", description = "San pham buyer thich, muc theo product")
public class WishlistController {

    private final WishlistService wishlist;

    @GetMapping
    @Operation(
        summary = "Liet ke wishlist",
        description = "Mon tam khong mua duoc van hien, kem co available + ly do, de buyer khoi thac mac."
    )
    @ApiResponse(responseCode = "200", description = "Danh sach da luu, rong neu chua co")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public List<WishlistItemResponse> list(@AuthenticationPrincipal Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return wishlist.getWishlist(buyerId);
    }

    @PostMapping("/items")
    @Operation(summary = "Luu/bo luu", description = "Chua luu thi luu, da luu thi bo. Mon an/khong ban duoc thi 404.")
    @ApiResponse(responseCode = "200", description = "Trang thai sau toggle")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "404", description = "Product khong ton tai hoac dang an")
    @SecurityRequirement(name = "bearerAuth")
    public WishlistToggleResponse toggle(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody WishlistToggleRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return wishlist.toggle(buyerId, request.productId());
    }

    @DeleteMapping("/items/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoa khoi wishlist")
    @ApiResponse(responseCode = "204", description = "Da xoa")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "404", description = "Product khong co trong wishlist")
    @SecurityRequirement(name = "bearerAuth")
    public void remove(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id product") @PathVariable UUID productId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        wishlist.remove(buyerId, productId);
    }
}
