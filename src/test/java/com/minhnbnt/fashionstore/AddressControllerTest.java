package com.minhnbnt.fashionstore;

import com.minhnbnt.fashionstore.users.AddressRequest;
import com.minhnbnt.fashionstore.users.CreateUserRequest;
import com.minhnbnt.fashionstore.users.UserRepository;
import com.minhnbnt.fashionstore.users.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private UserService users;

    @Autowired
    private UserRepository userRepository;

    private void signup(String authId, AddressRequest... addresses) {
        users.createUser(authId, new CreateUserRequest("Nguyen Van A", null, null, List.of(addresses)));
    }

    @Test
    void addedAddressBecomesDefaultWhenListEmpty() throws Exception {
        signup("user");

        mockMvc.perform(post("/api/users/me/addresses")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"city": "Hanoi"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.city").value("Hanoi"))
            .andExpect(jsonPath("$.default").value(true));
    }

    @Test
    void addedAddressIsNonDefaultWhenOneExists() throws Exception {
        signup("user", new AddressRequest(null, null, null, null, "Hanoi"));

        mockMvc.perform(post("/api/users/me/addresses")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"city": "Hue"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.city").value("Hue"))
            .andExpect(jsonPath("$.default").value(false));

        var addresses = userRepository.findByAuthId("user").orElseThrow().getAddresses();
        assertThat(addresses).hasSize(2);
        assertThat(addresses.get(0).isDefault()).isTrue();
    }

    @Test
    void keepsUntouchedFieldsOnEdit() throws Exception {
        signup("user", new AddressRequest("12", "Nguyen Trai", "Ben Thanh", "District 1", "Saigon"));
        long id = userRepository.findByAuthId("user").orElseThrow().getAddresses().get(0).getId();

        mockMvc.perform(put("/api/users/me/addresses/{id}", id)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"city": "Da Nang"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.city").value("Da Nang"))
            .andExpect(jsonPath("$.houseNumber").value("12"))
            .andExpect(jsonPath("$.street").value("Nguyen Trai"))
            .andExpect(jsonPath("$.ward").value("Ben Thanh"))
            .andExpect(jsonPath("$.district").value("District 1"));
    }

    @Test
    void emptyStringKeepsPreviousValue() throws Exception {
        signup("user", new AddressRequest("12", "Nguyen Trai", null, null, "Saigon"));
        long id = userRepository.findByAuthId("user").orElseThrow().getAddresses().get(0).getId();

        mockMvc.perform(put("/api/users/me/addresses/{id}", id)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"street": ""}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.street").value("Nguyen Trai"));
    }

    @Test
    void editsDefaultAddressKeepsItDefault() throws Exception {
        signup("user", new AddressRequest(null, null, null, null, "Hanoi"));
        long id = userRepository.findByAuthId("user").orElseThrow().getAddresses().get(0).getId();

        mockMvc.perform(put("/api/users/me/addresses/{id}", id)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"city": "Hue"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.city").value("Hue"))
            .andExpect(jsonPath("$.default").value(true));
    }

    @Test
    void changesDefaultAddress() throws Exception {
        signup("user",
            new AddressRequest(null, null, null, null, "Hanoi"),
            new AddressRequest(null, null, null, null, "Hue"));
        var addresses = userRepository.findByAuthId("user").orElseThrow().getAddresses();
        long id = addresses.get(1).getId();

        mockMvc.perform(put("/api/users/me/addresses/{id}/default", id)
                .with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.city").value("Hue"))
            .andExpect(jsonPath("$.default").value(true));

        var updated = userRepository.findByAuthId("user").orElseThrow().getAddresses();
        assertThat(updated.get(0).isDefault()).isFalse();
        assertThat(updated.get(1).isDefault()).isTrue();
    }

    @Test
    void rejectsUnknownAddress() throws Exception {
        signup("user");

        mockMvc.perform(put("/api/users/me/addresses/{id}", 999999)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"city": "Hue"}
                    """))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/users/me/addresses/{id}", 999999)
                .with(jwt()))
            .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/users/me/addresses/{id}/default", 999999)
                .with(jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void rejectsOtherShoppersAddress() throws Exception {
        signup("user", new AddressRequest(null, null, null, null, "Hanoi"));
        signup("other", new AddressRequest(null, null, null, null, "Hue"));
        long othersId = userRepository.findByAuthId("other").orElseThrow().getAddresses().get(0).getId();

        mockMvc.perform(put("/api/users/me/addresses/{id}", othersId)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"city": "Da Nang"}
                    """))
            .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/users/me/addresses/{id}", othersId)
                .with(jwt()))
            .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/users/me/addresses/{id}/default", othersId)
                .with(jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deletesNonDefaultAddress() throws Exception {
        signup("user",
            new AddressRequest(null, null, null, null, "Hanoi"),
            new AddressRequest(null, null, null, null, "Hue"));
        long id = userRepository.findByAuthId("user").orElseThrow().getAddresses().get(1).getId();

        mockMvc.perform(delete("/api/users/me/addresses/{id}", id)
                .with(jwt()))
            .andExpect(status().isNoContent());

        var addresses = userRepository.findByAuthId("user").orElseThrow().getAddresses();
        assertThat(addresses).hasSize(1);
        assertThat(addresses.get(0).isDefault()).isTrue();
    }

    @Test
    void deletingDefaultPromotesEarliestSurvivor() throws Exception {
        signup("user",
            new AddressRequest(null, null, null, null, "Hanoi"),
            new AddressRequest(null, null, null, null, "Hue"),
            new AddressRequest(null, null, null, null, "Saigon"));
        var addresses = userRepository.findByAuthId("user").orElseThrow().getAddresses();
        long defaultId = addresses.get(0).getId();

        mockMvc.perform(delete("/api/users/me/addresses/{id}", defaultId)
                .with(jwt()))
            .andExpect(status().isNoContent());

        var survivors = userRepository.findByAuthId("user").orElseThrow().getAddresses();
        assertThat(survivors).hasSize(2);
        assertThat(survivors.get(0).isDefault()).isTrue();
        assertThat(survivors.get(1).isDefault()).isFalse();
    }

    @Test
    void deletingOnlyAddressLeavesListEmpty() throws Exception {
        signup("user", new AddressRequest(null, null, null, null, "Hanoi"));
        long id = userRepository.findByAuthId("user").orElseThrow().getAddresses().get(0).getId();

        mockMvc.perform(delete("/api/users/me/addresses/{id}", id)
                .with(jwt()))
            .andExpect(status().isNoContent());

        assertThat(userRepository.findByAuthId("user").orElseThrow().getAddresses()).isEmpty();
    }

    @Test
    void addressEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/users/me/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"city": "Hanoi"}
                    """))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/users/me/addresses/{id}", 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"city": "Hanoi"}
                    """))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/users/me/addresses/{id}", 1))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/users/me/addresses/{id}/default", 1))
            .andExpect(status().isUnauthorized());
    }
}
