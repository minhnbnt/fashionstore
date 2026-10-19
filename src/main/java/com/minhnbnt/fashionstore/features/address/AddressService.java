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
        var hasDefault = false;
        for (var address : addresses.findByUserIdOrderByCreatedAtAsc(userId)) {
            if (address.isDefaultAddress()) {
                hasDefault = true;
                break;
            }
        }

        var created = Address.create(
            userId,
            request.houseNumber(),
            request.street(),
            request.ward(),
            request.district(),
            request.city(),
            !hasDefault
        );

        return toResponse(addresses.save(created));
    }

    @Transactional
    public AddressResponse updateAddress(@NonNull UUID userId, @NonNull UUID id, @NonNull AddressRequest request) {
        var address = requireUserAddress(userId, id);
        address.updateDetails(
            request.houseNumber(),
            request.street(),
            request.ward(),
            request.district(),
            request.city()
        );

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

        var wasDefault = address.isDefaultAddress();
        addresses.delete(address);

        if (wasDefault && !survivors.isEmpty()) {
            survivors.get(0).makeDefault();
            addresses.save(survivors.get(0));
        }
    }

    @Transactional
    public AddressResponse changeDefaultAddress(@NonNull UUID userId, @NonNull UUID id) {
        var address = requireUserAddress(userId, id);

        for (var other : addresses.findByUserIdOrderByCreatedAtAsc(userId)) {
            if (other.getId().equals(id)) {
                continue;
            }
            other.clearDefault();
            addresses.save(other);
        }

        address.makeDefault();
        return toResponse(addresses.save(address));
    }

    private Address requireUserAddress(UUID userId, UUID id) {
        var address = addresses.findByIdAndUserId(id, userId).orElse(null);
        if (address == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dia chi khong ton tai hoac khong phai cua ban");
        }

        return address;
    }

    private static AddressResponse toResponse(Address address) {
        return new AddressResponse(
            address.getId(),
            address.getHouseNumber(),
            address.getStreet(),
            address.getWard(),
            address.getDistrict(),
            address.getCity(),
            address.isDefaultAddress()
        );
    }
}
