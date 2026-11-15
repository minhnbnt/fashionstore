package com.minhnbnt.fashionstore.features.order.repository;

import com.minhnbnt.fashionstore.model.ReturnEvidence;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReturnEvidenceRepository extends CrudRepository<ReturnEvidence, UUID> {

    List<ReturnEvidence> findByReturnRequestIdOrderBySortOrderAsc(UUID returnRequestId);

    List<ReturnEvidence> findByReturnRequestIdIn(Collection<UUID> returnRequestIds);
}
