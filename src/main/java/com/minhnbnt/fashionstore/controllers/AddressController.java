package com.minhnbnt.fashionstore.controllers;

import com.minhnbnt.fashionstore.users.AddressRequest;
import com.minhnbnt.fashionstore.users.UserResponse;
import com.minhnbnt.fashionstore.users.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final UserService users;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse.AddressResponse addAddress(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody AddressRequest request
    ) {
        return users.addAddress(jwt.getSubject(), request);
    }

    @PutMapping("/{id}")
    public UserResponse.AddressResponse updateAddress(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable long id,
        @RequestBody AddressRequest request
    ) {
        return users.updateAddress(jwt.getSubject(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        users.deleteAddress(jwt.getSubject(), id);
    }

    @PutMapping("/{id}/default")
    public UserResponse.AddressResponse changeDefaultAddress(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable long id
    ) {
        return users.changeDefaultAddress(jwt.getSubject(), id);
    }
}
