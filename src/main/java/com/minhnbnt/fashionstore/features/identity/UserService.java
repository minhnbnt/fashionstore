package com.minhnbnt.fashionstore.features.identity;

import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.identity.UserResponse;
import com.minhnbnt.fashionstore.model.UserProfile;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository users;

    @Transactional
    public UserResponse upsert(@NonNull UUID id, @NonNull CreateUserRequest request) {

        var existing = users.findById(id);
        if (existing.isPresent()) {
            var profile = existing.get();
            profile.changeEmail(request.email());
            profile.rename(request.displayName());
            profile.changeAvatar(request.avatarKey());
            return toResponse(users.save(profile));
        }

        var created = UserProfile.create(
            id,
            request.email(),
            request.displayName(),
            request.avatarKey()
        );

        return toResponse(users.save(created));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {

        var profile = users.findById(id).orElse(null);
        if (profile == null) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Profile not found, call POST /api/users first"
            );
        }

        return toResponse(profile);
    }

    private static UserResponse toResponse(UserProfile profile) {
        return new UserResponse(
            profile.getId(),
            profile.getEmail(),
            profile.getDisplayName(),
            profile.getAvatarKey(),
            profile.getCreatedAt()
        );
    }
}
