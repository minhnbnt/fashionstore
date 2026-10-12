package com.minhnbnt.fashionstore.users;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    Optional<Address> findByIdAndUserAuthId(Long id, String authId);

    // Bulk update so the clearing hits the database before any row is
    // promoted: row-by-row updates can flush in an order that briefly
    // leaves two defaults and trips the one-default rule.
    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.user.id = :userId")
    void clearDefaultAddresses(@Param("userId") long userId);
}
