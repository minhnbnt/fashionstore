package com.minhnbnt.fashionstore.features.address;

import com.minhnbnt.fashionstore.dto.address.AddressRequest;
import com.minhnbnt.fashionstore.dto.address.AddressResponse;
import com.minhnbnt.fashionstore.features.address.repository.AddressRepository;
import com.minhnbnt.fashionstore.model.Address;
import com.minhnbnt.fashionstore.model.AddressLabel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddressService {

    public static final int MAX_ADDRESSES_PER_USER = 10;

    private final AddressRepository addresses;

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(@NonNull UUID userId) {
        var responses = new ArrayList<AddressResponse>();

        for (var address : addresses.findByUserIdOrderByCreatedAtAsc(userId)) {
            responses.add(toResponse(address));
        }

        return responses;
    }

    @Transactional
    public AddressResponse addAddress(@NonNull UUID userId, @NonNull AddressRequest request) {
        if (addresses.countByUserId(userId) >= MAX_ADDRESSES_PER_USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum of 10 addresses reached");
        }

        var isFirst = addresses.countByUserId(userId) == 0;
        var makeDefault = isFirst;
        if (Boolean.TRUE.equals(request.defaultAddress())) {
            makeDefault = true;
            addresses.clearDefaults(userId);
        }

        var recipient = "Khách hàng";
        if (request.recipientName() != null && !request.recipientName().isBlank()) {
            recipient = request.recipientName().trim();
        }

        var phone = "0900000000";
        if (request.phoneNumber() != null && !request.phoneNumber().isBlank()) {
            phone = request.phoneNumber().trim();
        }

        var detail = request.detailAddress();
        if (detail == null || detail.isBlank()) {
            var house = "";
            if (request.houseNumber() != null) {
                house = request.houseNumber().trim();
            }
            var street = "";
            if (request.street() != null) {
                street = request.street().trim();
            }
            var combined = (house + " " + street).trim();
            if (!combined.isEmpty()) {
                detail = combined;
            }
        }

        var label = AddressLabel.HOME;
        if (request.label() != null) {
            label = request.label();
        }

        var created = Address.create(
            userId,
            recipient,
            phone,
            detail,
            request.houseNumber(),
            request.street(),
            request.ward(),
            request.district(),
            request.city(),
            label,
            makeDefault
        );

        return toResponse(addresses.save(created));
    }

    @Transactional
    public AddressResponse updateAddress(@NonNull UUID userId, @NonNull UUID id, @NonNull AddressRequest request) {
        var address = requireUserAddress(userId, id);

        var detail = request.detailAddress();
        if (detail == null || detail.isBlank()) {
            var house = "";
            if (request.houseNumber() != null) {
                house = request.houseNumber().trim();
            }
            var street = "";
            if (request.street() != null) {
                street = request.street().trim();
            }
            var combined = (house + " " + street).trim();
            if (!combined.isEmpty()) {
                detail = combined;
            }
        }

        address.updateDetails(
            request.recipientName(),
            request.phoneNumber(),
            detail,
            request.houseNumber(),
            request.street(),
            request.ward(),
            request.district(),
            request.city(),
            request.label()
        );

        if (Boolean.TRUE.equals(request.defaultAddress())) {
            addresses.clearDefaults(userId);
            address.makeDefault();
        }

        return toResponse(addresses.save(address));
    }

    @Transactional
    public void deleteAddress(@NonNull UUID userId, @NonNull UUID id) {
        var address = requireUserAddress(userId, id);

        var survivors = new ArrayList<Address>();
        for (var other : addresses.findByUserIdOrderByCreatedAtAsc(userId)) {
            if (!other.getId().equals(id)) {
                survivors.add(other);
            }
        }

        addresses.delete(address);

        if (address.isDefaultAddress() && !survivors.isEmpty()) {
            var next = survivors.get(0);
            next.makeDefault();
            addresses.save(next);
        }
    }

    @Transactional
    public AddressResponse changeDefaultAddress(@NonNull UUID userId, @NonNull UUID id) {
        var address = requireUserAddress(userId, id);
        addresses.clearDefaults(userId);
        address.makeDefault();

        return toResponse(addresses.save(address));
    }

    private Address requireUserAddress(UUID userId, UUID id) {
        var address = addresses.findByIdAndUserId(id, userId).orElse(null);
        if (address == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found");
        }

        return address;
    }

    private static AddressResponse toResponse(Address address) {
        return new AddressResponse(
            address.getId(),
            address.getRecipientName(),
            address.getPhoneNumber(),
            address.getDetailAddress(),
            address.getHouseNumber(),
            address.getStreet(),
            address.getWard(),
            address.getDistrict(),
            address.getCity(),
            address.getLabel(),
            address.isDefaultAddress()
        );
    }
}
