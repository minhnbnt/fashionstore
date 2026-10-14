package com.minhnbnt.fashionstore.features.identity;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    void upsertCreatesProfileOnFirstLogin() {
        var id = UUID.randomUUID();

        var created = userService.upsert(id, new CreateUserRequest("create@t.local", "An", "avatar/1.png"));

        assertThat(created.id()).isEqualTo(id);
        assertThat(created.email()).isEqualTo("create@t.local");
        assertThat(created.avatarKey()).isEqualTo("avatar/1.png");
    }

    @Test
    void upsertUpdatesProfileOnNextLogin() {
        var id = UUID.randomUUID();
        userService.upsert(id, new CreateUserRequest("update-old@t.local", "An", null));

        var updated = userService.upsert(id, new CreateUserRequest("update-new@t.local", "Binh", null));

        assertThat(updated.email()).isEqualTo("update-new@t.local");
        assertThat(updated.displayName()).isEqualTo("Binh");
    }
}
