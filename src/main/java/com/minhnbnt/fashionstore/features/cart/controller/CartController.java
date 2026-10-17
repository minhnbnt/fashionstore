package com.minhnbnt.fashionstore.features.cart.controller;

import com.minhnbnt.fashionstore.dto.cart.CartCheckoutRequest;
import com.minhnbnt.fashionstore.dto.cart.CartItemRequest;
import com.minhnbnt.fashionstore.dto.cart.CartResponse;
import com.minhnbnt.fashionstore.dto.cart.UpdateCartItemRequest;
import com.minhnbnt.fashionstore.dto.order.OrderResponse;
import com.minhnbnt.fashionstore.features.cart.CartCheckoutBlockedException;
import com.minhnbnt.fashionstore.features.cart.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Gio hang buyer va checkout nhieu shop mot lan")
public class CartController {

    private final CartService carts;

    @GetMapping
    @Operation(summary = "Xem gio hang", description = "Gia va ton hien thi theo du lieu live, khong giu truoc.")
    @ApiResponse(responseCode = "200", description = "Gio hang, rong neu chua co gi")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public CartResponse view(@AuthenticationPrincipal Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return carts.getCart(buyerId);
    }

    @PostMapping("/items")
    @Operation(summary = "Them vao gio", description = "Variant da co thi cong don so luong.")
    @ApiResponse(responseCode = "200", description = "Gio hang sau khi them")
    @ApiResponse(responseCode = "400", description = "So luong nho hon 1")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "404", description = "Variant khong ton tai")
    @SecurityRequirement(name = "bearerAuth")
    public CartResponse addItem(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CartItemRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return carts.addItem(buyerId, request);
    }

    @PutMapping("/items/{variantId}")
    @Operation(summary = "Doi so luong", description = "Muon xoa han thi dung DELETE.")
    @ApiResponse(responseCode = "200", description = "Gio hang sau khi doi")
    @ApiResponse(responseCode = "400", description = "So luong nho hon 1")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "404", description = "Variant khong co trong gio")
    @SecurityRequirement(name = "bearerAuth")
    public CartResponse updateQuantity(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id variant") @PathVariable UUID variantId,
        @Valid @RequestBody UpdateCartItemRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return carts.updateQuantity(buyerId, variantId, request.quantity());
    }

    @DeleteMapping("/items/{variantId}")
    @Operation(summary = "Xoa item khoi gio")
    @ApiResponse(responseCode = "200", description = "Gio hang sau khi xoa")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "404", description = "Variant khong co trong gio")
    @SecurityRequirement(name = "bearerAuth")
    public CartResponse removeItem(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id variant") @PathVariable UUID variantId
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return carts.removeItem(buyerId, variantId);
    }

    @DeleteMapping
    @Operation(summary = "Xoa sach gio")
    @ApiResponse(responseCode = "200", description = "Gio hang rong")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public CartResponse clear(@AuthenticationPrincipal Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return carts.clear(buyerId);
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Checkout ca gio",
        description = "Kiem tra het truoc khi dat: hong cho nao thi khong dat don nao va tra ve tung item loi. "
            + "Dat thi tach 1 don 1 shop, dia chi giao hang chup lai vao tung don."
    )
    @ApiResponse(responseCode = "201", description = "Danh sach don vua dat, moi shop 1 don")
    @ApiResponse(responseCode = "400", description = "Gio rong hoac body khong hop le")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "409", description = "Co item khong checkout duoc, xem items")
    @SecurityRequirement(name = "bearerAuth")
    public List<OrderResponse> checkout(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CartCheckoutRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return carts.checkout(buyerId, request);
    }

    @ExceptionHandler(CartCheckoutBlockedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail checkoutBlocked(CartCheckoutBlockedException e) {
        var problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Checkout blocked, remove failed items and retry"
        );
        problem.setTitle("Checkout blocked");
        problem.setProperty("items", e.getItems());

        return problem;
    }
}
