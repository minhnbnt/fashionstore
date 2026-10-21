package com.minhnbnt.fashionstore.features.location;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.location.ProvinceResponse;
import com.minhnbnt.fashionstore.dto.location.WardResponse;
import com.minhnbnt.fashionstore.model.Region;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LocationServiceTest {

    @Autowired
    private LocationService locations;

    private String regionOfProvince(String code) {
        return locations.listProvinces().stream()
            .filter(province -> province.code().equals(code))
            .map(ProvinceResponse::region)
            .findFirst()
            .orElseThrow();
    }

    @Test
    void seeds34ProvincesWithRegions() {
        assertThat(locations.listProvinces()).hasSize(34);
        assertThat(regionOfProvince("01")).isEqualTo("NORTH");
        assertThat(regionOfProvince("48")).isEqualTo("CENTRAL");
        assertThat(regionOfProvince("79")).isEqualTo("SOUTH");
    }

    @Test
    void listsWardsOfProvince() {
        assertThat(locations.listWards("01")).contains(new WardResponse("00004", "Phường Ba Đình"));
    }

    @Test
    void listWardsRejectsUnknownProvince() {
        assertThatThrownBy(() -> locations.listWards("99"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void resolvesWardWithProvinceAndRegion() {
        var resolved = locations.resolve("01", "00004");

        assertThat(resolved.provinceName()).isEqualTo("Thành phố Hà Nội");
        assertThat(resolved.region()).isEqualTo(Region.NORTH);
        assertThat(resolved.wardName()).isEqualTo("Phường Ba Đình");
    }

    @Test
    void resolveRejectsWardOfOtherProvince() {
        assertThatThrownBy(() -> locations.resolve("79", "00004"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getReason()).isEqualTo("Ward does not belong to province");
            });
    }

    @Test
    void resolveRejectsUnknownProvince() {
        assertThatThrownBy(() -> locations.resolve("99", "00004"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(e.getReason()).isEqualTo("Unknown province");
            });
    }

    @Test
    void regionOfRejectsUnknownProvince() {
        assertThat(locations.regionOf("48")).isEqualTo(Region.CENTRAL);
        assertThatThrownBy(() -> locations.regionOf("99"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
