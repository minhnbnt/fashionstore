package com.minhnbnt.fashionstore.features.address.controller;

import com.minhnbnt.fashionstore.dto.address.AddressRequest;
import com.minhnbnt.fashionstore.dto.address.AddressResponse;
import com.minhnbnt.fashionstore.features.address.AddressService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me/addresses")
@RequiredArgsConstructor
@Tag(name = "Addresses", description = "So dia chi giao hang cua buyer")
public class AddressController {

    private final AddressService addresses;

    @GetMapping
    @Operation(summary = "Liet ke dia chi", description = "Dia chi xep theo thu tu tao, lau doi nhat truoc.")
    @ApiResponse(responseCode = "200", description = "Danh sach dia chi, rong neu chua co")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public List<AddressResponse> list(@AuthenticationPrincipal Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return addresses.getAddresses(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Them dia chi", description = "Toi da 10 dia chi. Dia chi dau tien tu thanh default.")
    @ApiResponse(responseCode = "201", description = "Dia chi vua tao")
    @ApiResponse(responseCode = "400", description = "Du lieu khong hop le hoac da dat gioi han 10 dia chi")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public AddressResponse add(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody AddressRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return addresses.addAddress(userId, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sua dia chi", description = "Field trong thi giu gia tri cu, ke ca dia chi default.")
    @ApiResponse(responseCode = "200", description = "Dia chi sau khi sua")
    @ApiResponse(responseCode = "400", description = "Du lieu khong hop le")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "404", description = "Dia chi khong ton tai hoac khong phai cua ban")
    @SecurityRequirement(name = "bearerAuth")
    public AddressResponse update(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id dia chi") @PathVariable UUID id,
        @Valid @RequestBody AddressRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return addresses.updateAddress(userId, id, request);
    }

    @PutMapping("/{id}/default")
    @Operation(summary = "Doi default", description = "Dat dia chi nay lam default, default cu tu nha.")
    @ApiResponse(responseCode = "200", description = "Dia chi default moi")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "404", description = "Dia chi khong ton tai hoac khong phai cua ban")
    @SecurityRequirement(name = "bearerAuth")
    public AddressResponse changeDefault(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id dia chi") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return addresses.changeDefaultAddress(userId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Xoa dia chi",
        description = "Xoa dung default ma con dia chi khac thi dia chi lau doi nhat len thay. "
            + "Xoa cai cuoi cung thi danh sach rong."
    )
    @ApiResponse(responseCode = "204", description = "Da xoa")
    @ApiResponse(responseCode = "401", description = "Thieu hoac sai JWT")
    @ApiResponse(responseCode = "404", description = "Dia chi khong ton tai hoac khong phai cua ban")
    @SecurityRequirement(name = "bearerAuth")
    public void delete(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "id dia chi") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        addresses.deleteAddress(userId, id);
    }
}
