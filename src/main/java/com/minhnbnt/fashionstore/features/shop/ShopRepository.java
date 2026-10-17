package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.Shop;
import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShopRepository extends CrudRepository<Shop, UUID> {

    Optional<Shop> findBySlug(String slug);

    /**
     * SELECT ... FOR UPDATE: mọi thay đổi trạng thái/hồ sơ shop đi qua đây để 2 thao tác đồng thời
     * (admin tạm khóa, owner sửa hồ sơ) không ghi đè nhau.
     */
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<Shop> findLockedById(UUID id);
}
