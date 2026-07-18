package com.minhnbnt.fashionstore.controllers;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/api/public/hello")
    public Map<String, String> publicHello(@AuthenticationPrincipal @Nullable Jwt jwt) {

        var message = "Hello, world!";
        if (jwt != null) {
            message = String.format("Hello, %s!", jwt.getSubject());
        }

        return Map.of("message", message);
    }
}
