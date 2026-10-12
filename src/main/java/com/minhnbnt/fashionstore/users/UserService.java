package com.minhnbnt.fashionstore.users;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;

    private final AddressRepository addresses;

    @Transactional
    public UserResponse createUser(String authId, CreateUserRequest request) {
        if (users.existsByAuthId(authId)) {
            throw new DuplicateUserException(authId);
        }

        var user = new User();
        user.setAuthId(authId);
        user.setName(request.name());
        user.setDateOfBirth(request.dateOfBirth());
        user.setPhone(request.phone());

        // The first address a user ever gets becomes the default one.
        boolean needsDefault = true;
        for (var addressRequest : request.addresses()) {
            var address = new Address();
            address.setUser(user);
            copyFields(address, addressRequest);
            address.setDefault(needsDefault);
            needsDefault = false;
            user.getAddresses().add(address);
        }

        return UserResponse.from(users.save(user));
    }

    @Transactional
    public UserResponse.AddressResponse addAddress(String authId, AddressRequest request) {
        var user = users.findByAuthId(authId).orElseThrow(() -> new UserNotFoundException(authId));

        var address = new Address();
        address.setUser(user);
        copyFields(address, request);
        address.setDefault(user.getAddresses().stream().noneMatch(Address::isDefault));
        user.getAddresses().add(address);
        users.save(user);

        return UserResponse.AddressResponse.from(address);
    }

    @Transactional
    public UserResponse.AddressResponse updateAddress(String authId, long id, AddressRequest request) {
        var address = addresses.findByIdAndUserAuthId(id, authId)
            .orElseThrow(() -> new AddressNotFoundException(id));
        mergeFields(address, request);
        return UserResponse.AddressResponse.from(address);
    }

    @Transactional
    public void deleteAddress(String authId, long id) {
        var address = addresses.findByIdAndUserAuthId(id, authId)
            .orElseThrow(() -> new AddressNotFoundException(id));
        var user = address.getUser();
        boolean wasDefault = address.isDefault();
        user.getAddresses().remove(address);
        // Flush the removal first: promoting a survivor before the old
        // default's row is gone would briefly leave two defaults.
        users.flush();
        if (wasDefault && !user.getAddresses().isEmpty()) {
            // Addresses load oldest first, so the survivor at the front takes over as default.
            user.getAddresses().get(0).setDefault(true);
        }
    }

    @Transactional
    public UserResponse.AddressResponse changeDefaultAddress(String authId, long id) {
        var address = addresses.findByIdAndUserAuthId(id, authId)
            .orElseThrow(() -> new AddressNotFoundException(id));
        addresses.clearDefaultAddresses(address.getUser().getId());
        address.getUser().getAddresses().forEach(a -> a.setDefault(false));
        address.setDefault(true);
        return UserResponse.AddressResponse.from(address);
    }

    private static void copyFields(Address address, AddressRequest request) {
        address.setHouseNumber(request.houseNumber());
        address.setStreet(request.street());
        address.setWard(request.ward());
        address.setDistrict(request.district());
        address.setCity(request.city());
    }

    // Empty (missing or blank) fields keep their previous value.
    private static void mergeFields(Address address, AddressRequest request) {
        if (!isEmpty(request.houseNumber())) {
            address.setHouseNumber(request.houseNumber());
        }
        if (!isEmpty(request.street())) {
            address.setStreet(request.street());
        }
        if (!isEmpty(request.ward())) {
            address.setWard(request.ward());
        }
        if (!isEmpty(request.district())) {
            address.setDistrict(request.district());
        }
        if (!isEmpty(request.city())) {
            address.setCity(request.city());
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }
}
