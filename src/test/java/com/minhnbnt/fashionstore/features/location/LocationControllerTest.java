package com.minhnbnt.fashionstore.features.location;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.location.ProvinceResponse;
import com.minhnbnt.fashionstore.dto.location.WardResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocationController.class)
@Import(SecurityConfig.class)
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private LocationService locations;

    @Test
    void listsProvincesWithoutJwt() throws Exception {
        when(locations.listProvinces()).thenReturn(List.of(new ProvinceResponse("01", "Thành phố Hà Nội", "NORTH")));

        mockMvc.perform(get("/api/public/locations/provinces"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("01"))
            .andExpect(jsonPath("$[0].region").value("NORTH"));
    }

    @Test
    void listsWardsWithoutJwt() throws Exception {
        when(locations.listWards("01")).thenReturn(List.of(new WardResponse("00004", "Phường Ba Đình")));

        mockMvc.perform(get("/api/public/locations/provinces/01/wards"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("00004"));
    }
}
