package com.minhnbnt.fashionstore.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;

import java.util.Map;

@Configuration
public class OpenApiProblemCustomizer {

    private static final String PROBLEM_MEDIA_TYPE = "application/problem+json";

    private static final String PROBLEM_SCHEMA_REF = "#/components/schemas/ProblemDetail";

    private static final Map<String, String> ERRORS = Map.of(
        "400", "Invalid request data",
        "401", "Missing or invalid JWT",
        "403", "Forbidden",
        "404", "Resource not found",
        "409", "Data or state conflict"
    );

    @Bean
    public OpenApiCustomizer problemResponses() {
        return this::addProblemResponses;
    }

    private void addProblemResponses(OpenAPI openApi) {

        registerProblemDetailSchema(openApi);

        var ref = new Schema<>().$ref(PROBLEM_SCHEMA_REF);

        for (var path : openApi.getPaths().values()) {
            for (var operation : path.readOperations()) {
                var responses = operation.getResponses();
                for (var error : ERRORS.entrySet()) {
                    var response = responses.get(error.getKey());
                    if (response == null) {
                        response = new ApiResponse().description(error.getValue());
                        responses.addApiResponse(error.getKey(), response);
                    }
                    ensureProblemContent(response, ref);
                }
            }
        }
    }

    private static void registerProblemDetailSchema(OpenAPI openApi) {
        var components = openApi.getComponents();
        if (components == null) {
            components = new Components();
            openApi.setComponents(components);
        }

        var schemas = ModelConverters.getInstance().read(ProblemDetail.class);
        for (var entry : schemas.entrySet()) {
            components.addSchemas(entry.getKey(), entry.getValue());
        }
    }

    private static void ensureProblemContent(ApiResponse response, Schema<?> ref) {
        var content = response.getContent();
        if (content == null) {
            content = new Content();
            response.setContent(content);
        }
        if (!content.containsKey(PROBLEM_MEDIA_TYPE)) {
            content.addMediaType(PROBLEM_MEDIA_TYPE, new MediaType().schema(ref));
        }
    }
}
