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

        // Write to local docs
        var localDocs = Paths.get("docs", "openapi.json");
        if (Files.exists(localDocs.getParent())) {
            Files.writeString(localDocs, response);
        }

        // Write to worktree frontend docs
        var worktreeUi = Paths.get("..", "fashionstore-ui-seller-registration-ekyc", "docs", "openapi.json");
        if (Files.exists(worktreeUi.getParent())) {
            Files.writeString(worktreeUi, response);
        }

        var sellerOrderUi = Paths.get("..", "..", "fashionstore-ui", ".worktrees", "seller-order-processing", "docs", "openapi.json");
        if (Files.exists(sellerOrderUi.getParent())) {
            Files.writeString(sellerOrderUi, response);
        }

        // Write to fashionstore-ui docs if exists
        var targetMainUi = Paths.get("..", "fashionstore-ui", "docs", "openapi.json");
        if (Files.exists(targetMainUi.getParent())) {
            Files.writeString(targetMainUi, response);
        }
    }
}
