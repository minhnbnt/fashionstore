package com.minhnbnt.fashionstore;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiExportTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exportOpenApiJson() throws Exception {
        var response = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Write to fashionstore-ui-stats docs
        var targetUi = Paths.get("..", "fashionstore-ui-stats", "docs", "openapi.json");
        Files.writeString(targetUi, response);

        // Also write to fashionstore-ui docs if exists
        var targetMainUi = Paths.get("..", "fashionstore-ui", "docs", "openapi.json");
        if (Files.exists(targetMainUi.getParent())) {
            Files.writeString(targetMainUi, response);
        }
    }
}
