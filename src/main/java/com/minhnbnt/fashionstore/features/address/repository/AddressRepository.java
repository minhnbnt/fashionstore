package com.minhnbnt.fashionstore.features.address.repository;

import com.minhnbnt.fashionstore.model.Address;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends CrudRepository<Address, UUID> {

    List<Address> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Address> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("UPDATE address SET is_default = false WHERE user_id = :userId")
    void clearDefaults(@Param("userId") UUID userId);

    long countByUserId(UUID userId);
}
