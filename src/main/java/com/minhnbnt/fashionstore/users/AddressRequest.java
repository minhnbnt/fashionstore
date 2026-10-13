package com.minhnbnt.fashionstore.users;

public record AddressRequest(
    String houseNumber,
    String street,
    String ward,
    String district,
    String city
) {
}
