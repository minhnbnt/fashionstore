package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.features.identity.AccountProvisioner;
import com.minhnbnt.fashionstore.infrastructure.GoTrueAdminClient;
import com.minhnbnt.fashionstore.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminAccountBootstrapTest {

    private static final String EMAIL = "admin@fashionstore.local";

    private static final ApplicationArguments ARGS = new DefaultApplicationArguments();

    private final GoTrueAdminClient gotrue = mock(GoTrueAdminClient.class);

    private final AccountProvisioner provisioner = mock(AccountProvisioner.class);

    private AdminAccountBootstrap bootstrap(String email, String password) {
        return new AdminAccountBootstrap(gotrue, provisioner, email, password, "Admin");
    }

    @Test
    void createsAdminInGoTrueAndProfileWhenMissing() {
        var id = UUID.randomUUID();
        when(gotrue.isConfigured()).thenReturn(true);
        when(gotrue.findUserIdByEmail(EMAIL)).thenReturn(Optional.empty());
        when(gotrue.createUser(EMAIL, "Secret@123", "Admin", "admin")).thenReturn(id);

        bootstrap(EMAIL, "Secret@123").run(ARGS);

        verify(gotrue).createUser(EMAIL, "Secret@123", "Admin", "admin");
        verify(provisioner).ensureAccount(id, EMAIL, "Admin", UserRole.ADMIN);
    }

    @Test
    void promotesExistingUserWithoutTouchingPassword() {
        var id = UUID.randomUUID();
        when(gotrue.isConfigured()).thenReturn(true);
        when(gotrue.findUserIdByEmail(EMAIL)).thenReturn(Optional.of(id));

        bootstrap(EMAIL, "Secret@123").run(ARGS);

        verify(gotrue).setRole(id, "admin");
        verify(gotrue, never()).createUser(anyString(), anyString(), anyString(), anyString());
        verify(provisioner).ensureAccount(id, EMAIL, "Admin", UserRole.ADMIN);
    }

    @Test
    void existingUserNeedsNoPassword() {
        var id = UUID.randomUUID();
        when(gotrue.isConfigured()).thenReturn(true);
        when(gotrue.findUserIdByEmail(EMAIL)).thenReturn(Optional.of(id));

        bootstrap(EMAIL, "").run(ARGS);

        verify(gotrue).setRole(id, "admin");
    }

    @Test
    void blankEmailDisablesBootstrap() {
        bootstrap(" ", "Secret@123").run(ARGS);

        verifyNoInteractions(gotrue, provisioner);
    }

    @Test
    void skipsWithoutServiceRoleKey() {
        when(gotrue.isConfigured()).thenReturn(false);

        bootstrap(EMAIL, "Secret@123").run(ARGS);

        verify(gotrue, never()).findUserIdByEmail(anyString());
        verifyNoInteractions(provisioner);
    }

    @Test
    void skipsCreateWhenPasswordMissing() {
        when(gotrue.isConfigured()).thenReturn(true);
        when(gotrue.findUserIdByEmail(EMAIL)).thenReturn(Optional.empty());

        bootstrap(EMAIL, "").run(ARGS);

        verify(gotrue, never()).createUser(anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(provisioner);
    }

    @Test
    void goTrueFailureDoesNotStopStartup() {
        when(gotrue.isConfigured()).thenReturn(true);
        when(gotrue.findUserIdByEmail(EMAIL))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Identity provider request failed"));

        assertThatCode(() -> bootstrap(EMAIL, "Secret@123").run(ARGS)).doesNotThrowAnyException();

        verify(provisioner, never()).ensureAccount(any(), any(), any(), any());
    }
}
