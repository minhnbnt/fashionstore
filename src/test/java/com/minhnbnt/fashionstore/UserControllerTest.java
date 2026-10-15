package com.minhnbnt.fashionstore;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void createsUserWithNameOnly() throws Exception {
        mockMvc.perform(post("/api/users")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Nguyen Van A"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Nguyen Van A"))
            .andExpect(jsonPath("$.addresses").isEmpty());
    }

    @Test
    void firstAddressBecomesDefault() throws Exception {
        mockMvc.perform(post("/api/users")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Nguyen Van A",
                        "addresses": [{"city": "Hanoi"}]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.addresses[0].city").value("Hanoi"))
            .andExpect(jsonPath("$.addresses[0].default").value(true));
    }

    @Test
    void onlyFirstOfManyAddressesIsDefault() throws Exception {
        mockMvc.perform(post("/api/users")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Nguyen Van A",
                        "addresses": [
                            {"city": "Hanoi"},
                            {"city": "Hue"},
                            {"city": "Saigon"}
                        ]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.addresses[0].default").value(true))
            .andExpect(jsonPath("$.addresses[1].default").value(false))
            .andExpect(jsonPath("$.addresses[2].default").value(false));
    }

    @Test
    void echoesFullAddressBack() throws Exception {
        mockMvc.perform(post("/api/users")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Nguyen Van A",
                        "dateOfBirth": "1990-05-20",
                        "phone": "0901234567",
                        "addresses": [{
                            "houseNumber": "12",
                            "street": "Nguyen Trai",
                            "ward": "Ben Thanh",
                            "district": "District 1",
                            "city": "Saigon"
                        }]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.dateOfBirth").value("1990-05-20"))
            .andExpect(jsonPath("$.phone").value("0901234567"))
            .andExpect(jsonPath("$.addresses[0].houseNumber").value("12"))
            .andExpect(jsonPath("$.addresses[0].street").value("Nguyen Trai"))
            .andExpect(jsonPath("$.addresses[0].ward").value("Ben Thanh"))
            .andExpect(jsonPath("$.addresses[0].district").value("District 1"))
            .andExpect(jsonPath("$.addresses[0].city").value("Saigon"))
            .andExpect(jsonPath("$.addresses[0].default").value(true));
    }

    @Test
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/users")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "  "}
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsFutureDateOfBirth() throws Exception {
        mockMvc.perform(post("/api/users")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Nguyen Van A",
                        "dateOfBirth": "2999-01-01"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateSignup() throws Exception {
        String body = """
            {"name": "Nguyen Van A"}
            """;

        mockMvc.perform(post("/api/users")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isConflict());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Nguyen Van A"}
                    """))
            .andExpect(status().isUnauthorized());
    }
}
