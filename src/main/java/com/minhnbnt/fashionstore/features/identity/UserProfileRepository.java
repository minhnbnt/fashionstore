package com.minhnbnt.fashionstore.features.identity;

import com.minhnbnt.fashionstore.model.UserProfile;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface UserProfileRepository extends CrudRepository<UserProfile, UUID> {
}
