package com.minhnbnt.fashionstore.users;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record UserResponse(
    Long id,
    String name,
    LocalDate dateOfBirth,
    String phone,
    List<AddressResponse> addresses
) {

    static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getName(),
            user.getDateOfBirth(),
            user.getPhone(),
            user.getAddresses().stream().map(AddressResponse::from).toList()
        );
    }

    public record AddressResponse(
        Long id,
        String houseNumber,
        String street,
        String ward,
        String district,
        String city,
        @JsonProperty("default") boolean isDefault
    ) {

        static AddressResponse from(Address address) {
            return new AddressResponse(
                address.getId(),
                address.getHouseNumber(),
                address.getStreet(),
                address.getWard(),
                address.getDistrict(),
                address.getCity(),
                address.isDefault()
            );
        }
    }
}
