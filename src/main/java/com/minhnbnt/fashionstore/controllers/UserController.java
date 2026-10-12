package com.minhnbnt.fashionstore.controllers;

import com.minhnbnt.fashionstore.users.CreateUserRequest;
import com.minhnbnt.fashionstore.users.UserResponse;
import com.minhnbnt.fashionstore.users.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService users;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateUserRequest request
    ) {
        return users.createUser(jwt.getSubject(), request);
    }
}
