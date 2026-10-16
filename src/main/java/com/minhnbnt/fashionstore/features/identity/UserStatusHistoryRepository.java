package com.minhnbnt.fashionstore.features.identity;

import com.minhnbnt.fashionstore.model.UserStatusHistory;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface UserStatusHistoryRepository extends CrudRepository<UserStatusHistory, UUID> {
}
