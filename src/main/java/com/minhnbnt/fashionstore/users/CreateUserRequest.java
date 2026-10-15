package com.minhnbnt.fashionstore.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;
import java.util.List;

public record CreateUserRequest(
    @NotBlank String name,
    @Past LocalDate dateOfBirth,
    String phone,
    List<AddressRequest> addresses
) {

    public CreateUserRequest {
        addresses = addresses == null ? List.of() : List.copyOf(addresses);
    }
}
