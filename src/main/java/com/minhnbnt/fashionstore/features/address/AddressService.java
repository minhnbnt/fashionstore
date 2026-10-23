package com.minhnbnt.fashionstore.features.address;

import com.minhnbnt.fashionstore.dto.address.AddressRequest;
import com.minhnbnt.fashionstore.dto.address.AddressResponse;
import com.minhnbnt.fashionstore.features.address.repository.AddressRepository;
import com.minhnbnt.fashionstore.model.Address;
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
        if (request.defaultAddress()) {
            makeDefault = true;
            addresses.clearDefaults(userId);
        }

        var created = Address.create(
            userId,
            request.recipientName().trim(),
            request.phoneNumber().trim(),
            request.fullAddress().trim(),
            request.label(),
            makeDefault
        );

        return toResponse(addresses.save(created));
    }

    @Transactional
    public AddressResponse updateAddress(@NonNull UUID userId, @NonNull UUID id, @NonNull AddressRequest request) {
        var address = requireUserAddress(userId, id);

        address.updateDetails(
            request.recipientName().trim(),
            request.phoneNumber().trim(),
            request.fullAddress().trim(),
            request.label()
        );

        if (request.defaultAddress()) {
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
            address.getFullAddress(),
            address.getLabel(),
            address.isDefaultAddress()
        );
    }
}
