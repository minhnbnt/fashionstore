package com.minhnbnt.fashionstore.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiProblemCustomizerTest {

    private static final String PROBLEM_MEDIA_TYPE = "application/problem+json";

    private static OpenAPI apiWithOneOperation() {
        var api = new OpenAPI();
        var operation = new Operation();
        operation.setResponses(new ApiResponses().addApiResponse("200", new ApiResponse().description("OK")));
        api.path("/api/shops/mine", new PathItem().get(operation));
        return api;
    }

    private static void apply(OpenAPI api) {
        new OpenApiProblemCustomizer().problemResponses().customise(api);
    }

    @Test
    void addsProblemResponsesToEveryOperation() {
        var api = apiWithOneOperation();

        apply(api);

        var responses = api.getPaths().get("/api/shops/mine").getGet().getResponses();
        assertThat(responses.keySet()).contains("400", "401", "403", "404", "409");
    }

    @Test
    void errorResponsesUseProblemJsonWithProblemDetailSchema() {
        var api = apiWithOneOperation();

        apply(api);

        var responses = api.getPaths().get("/api/shops/mine").getGet().getResponses();
        for (var code : new String[]{"400", "401", "403", "404", "409"}) {
            var schema = responses.get(code).getContent().get(PROBLEM_MEDIA_TYPE).getSchema();
            assertThat(schema.get$ref()).isEqualTo("#/components/schemas/ProblemDetail");
        }
        assertThat(api.getComponents().getSchemas()).containsKey("ProblemDetail");
    }

    @Test
    void keepsExistingOkResponse() {
        var api = apiWithOneOperation();

        apply(api);

        var responses = api.getPaths().get("/api/shops/mine").getGet().getResponses();
        assertThat(responses.get("200").getDescription()).isEqualTo("OK");
    }

    @Test
    void enrichesExistingErrorResponseWithProblemBody() {
        var api = apiWithOneOperation();
        api.getPaths()
            .get("/api/shops/mine")
            .getGet()
            .getResponses()
            .addApiResponse("404", new ApiResponse().description("Shop khong ton tai"));

        apply(api);

        var notFound = api.getPaths().get("/api/shops/mine").getGet().getResponses().get("404");
        assertThat(notFound.getDescription()).isEqualTo("Shop khong ton tai");
        assertThat(notFound.getContent()).containsKey(PROBLEM_MEDIA_TYPE);
    }
}
