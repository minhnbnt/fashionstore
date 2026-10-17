package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.model.ShopMemberAudit;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface ShopMemberAuditRepository extends CrudRepository<ShopMemberAudit, UUID> {

    List<ShopMemberAudit> findByShopIdOrderByCreatedAtDesc(UUID shopId);
}
