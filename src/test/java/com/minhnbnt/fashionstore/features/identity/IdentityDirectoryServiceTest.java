package com.minhnbnt.fashionstore.features.identity;

import com.minhnbnt.fashionstore.dto.identity.AccountInfo;
import com.minhnbnt.fashionstore.infrastructure.GoTrueAdminClient;
import com.minhnbnt.fashionstore.model.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityDirectoryServiceTest {

    @Mock
    private UserProfileRepository users;

    @Mock
    private GoTrueAdminClient gotrue;

    @InjectMocks
    private IdentityDirectoryService directory;

    @Test
    void findByEmailUsesDatabaseWithoutCallingGoTrue() {
        var id = UUID.randomUUID();
        var profile = UserProfile.create(id, "staff@store.com", "Staff", null);

        when(users.findByEmailIgnoreCase("staff@store.com")).thenReturn(Optional.of(profile));

        var result = directory.findByEmail("  Staff@Store.COM ");

        assertThat(result).contains(new AccountInfo(id, "staff@store.com", "Staff"));
        verify(gotrue, never()).isConfigured();
        verify(gotrue, never()).findUserIdByEmail(any());
    }

    @Test
    void findByEmailReturnsEmptyWhenDatabaseMissAndGoTrueUnconfigured() {
        when(users.findByEmailIgnoreCase("staff@store.com")).thenReturn(Optional.empty());
        when(gotrue.isConfigured()).thenReturn(false);

        var result = directory.findByEmail("staff@store.com");

        assertThat(result).isEmpty();
        verify(gotrue, never()).findUserIdByEmail(any());
        verify(users, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void findByEmailUpsertsAndRereadsOnGoTrueHit() {
        var id = UUID.randomUUID();
        var profile = UserProfile.create(id, "staff@store.com", "staff@store.com", null);

        when(users.findByEmailIgnoreCase("staff@store.com")).thenReturn(Optional.empty());
        when(gotrue.isConfigured()).thenReturn(true);
        when(gotrue.findUserIdByEmail("staff@store.com")).thenReturn(Optional.of(id));
        when(users.findById(id)).thenReturn(Optional.of(profile));

        var result = directory.findByEmail("staff@store.com");

        assertThat(result).contains(new AccountInfo(id, "staff@store.com", "staff@store.com"));
        verify(users).insertIfAbsent(id, "staff@store.com", "staff@store.com");
        verify(users).findById(id);
    }
}
