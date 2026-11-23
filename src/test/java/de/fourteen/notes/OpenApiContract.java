package de.fourteen.notes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.ClassUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The API contract, docs/openapi.json, in the tests. springdoc describes the controllers
 * (reflection on the classes: types, paths, statuses); this class adds what they cannot tell and
 * checks every response of the API tests against the file: the operation and the status must be
 * described, and the body must match the schema of its content type. Import it next to the other
 * configuration of the test.
 *
 * <p>The responses are checked as plain JSON Schema (OpenAPI 3.1 schemas are 2020-12) with
 * networknt. The OpenAPI validator of Atlassian cannot run next to springdoc 3: it needs
 * swagger-core 2, springdoc 3 brings 3.
 */
@TestConfiguration(proxyBeanMethods = false)
class OpenApiContract {

    static final Path FILE = Path.of("docs", "openapi.json");

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Adds the login and the problem the exception handlers answer with. The operation ids are made
     * from method and path, because the generated ones (list_1) depend on the order in which Spring
     * finds the controllers.
     */
    @Bean
    OpenApiCustomizer whatTheControllersCannotTell() {
        return openApi -> {
            openApi.info(
                    new Info()
                            .title("Notes API")
                            .version("1")
                            .description(
                                    "The REST API of the notes app, used by its own frontend."));
            openApi.servers(List.of(new Server().url("/").description("This app")));
            Components components = openApi.getComponents();
            components.addSecuritySchemes(
                    "basic", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic"));
            openApi.addSecurityItem(new SecurityRequirement().addList("basic"));
            ModelConverters.getInstance(true)
                    .read(ProblemDetail.class)
                    .forEach(components::addSchemas);
            requiredUnlessNullable(components);
            openApi.getPaths().keySet().stream()
                    .map(OpenApiContract::resource)
                    .distinct()
                    .forEach(tag -> openApi.addTagsItem(new Tag().name(tag)));
            openApi.getPaths()
                    .forEach(
                            (path, item) ->
                                    item.readOperationsMap()
                                            .forEach(
                                                    (method, operation) ->
                                                            describe(operation, method, path)));
        };
    }

    /**
     * springdoc does not know JSpecify: a record component without @Nullable is required and never
     * null, one with @Nullable is present and may be null.
     */
    private static void requiredUnlessNullable(Components components) {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Record.class));
        for (var candidate : scanner.findCandidateComponents("de.fourteen.notes")) {
            Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), null);
            Schema<?> schema = components.getSchemas().get(type.getSimpleName());
            if (schema == null) continue;
            for (var component : type.getRecordComponents()) {
                var named = component.getAccessor().getAnnotation(JsonProperty.class);
                String name = named == null ? component.getName() : named.value();
                if (component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
                    schema.getProperties().get(name).addType("null");
                } else {
                    schema.addRequiredItem(name);
                }
            }
        }
    }

    /** /api/notes/{id} -> notes */
    private static String resource(String path) {
        String rest = path.substring("/api/".length());
        int slash = rest.indexOf('/');
        return slash < 0 ? rest : rest.substring(0, slash);
    }

    private static void describe(Operation operation, PathItem.HttpMethod method, String path) {
        StringBuilder id = new StringBuilder(method.name().toLowerCase(Locale.ROOT));
        Pattern.compile("[^A-Za-z]+")
                .splitAsStream(path)
                .filter(word -> !word.isEmpty())
                .forEach(
                        word ->
                                id.append(Character.toUpperCase(word.charAt(0)))
                                        .append(word.substring(1)));
        operation.setOperationId(id.toString());
        // One tag per resource (/api/notes/{id} -> notes) instead of the controller class name.
        operation.setTags(List.of(resource(path)));
        // The controllers declare no produces: springdoc writes */*, the answers are JSON.
        operation
                .getResponses()
                .values()
                .forEach(
                        response -> {
                            Content content = response.getContent();
                            if (content != null && content.containsKey("*/*")) {
                                content.addMediaType("application/json", content.remove("*/*"));
                            }
                        });
        Schema<?> problem = new Schema<>().$ref("#/components/schemas/ProblemDetail");
        operation
                .getResponses()
                .addApiResponse("401", new ApiResponse().description("No or wrong login"))
                .addApiResponse(
                        "default",
                        new ApiResponse()
                                .description("Failure")
                                .content(
                                        new Content()
                                                .addMediaType(
                                                        "application/problem+json",
                                                        new MediaType().schema(problem))));
    }

    /** Every response to a request under /api must match the contract. */
    @Bean
    MockMvcBuilderCustomizer responsesMatchTheContract() {
        return builder ->
                builder.alwaysExpect(
                        result -> {
                            if (result.getRequest().getRequestURI().startsWith("/api/")) {
                                check(JSON.readTree(Files.readString(FILE)), result);
                            }
                        });
    }

    private static void check(JsonNode contract, MvcResult result) throws IOException {
        String method = result.getRequest().getMethod().toLowerCase(Locale.ROOT);
        String path = result.getRequest().getRequestURI();
        int status = result.getResponse().getStatus();
        String what = method.toUpperCase(Locale.ROOT) + " " + path + " -> " + status;

        JsonNode operation = null;
        for (var item : contract.path("paths").properties()) {
            Pattern template = Pattern.compile(item.getKey().replaceAll("\\{[^}]+}", "[^/]+"));
            if (template.matcher(path).matches() && item.getValue().has(method)) {
                operation = item.getValue().get(method);
            }
        }
        if (operation == null) throw new AssertionError(what + ": not in the contract");

        JsonNode responses = operation.path("responses");
        JsonNode response =
                responses.has(String.valueOf(status))
                        ? responses.get(String.valueOf(status))
                        : responses.path("default");
        if (response.isMissingNode()) {
            throw new AssertionError(what + ": status not in the contract");
        }

        String body = result.getResponse().getContentAsString();
        if (body.isEmpty()) return;
        String type = result.getResponse().getContentType();
        type = type == null || !type.contains(";") ? type : type.substring(0, type.indexOf(';'));
        JsonNode content = response.path("content");
        JsonNode media = content.has(type) ? content.get(type) : content.path("*/*");
        if (media.isMissingNode()) {
            throw new AssertionError(what + ": content type " + type + " not in the contract");
        }
        List<Error> errors = validate(contract.path("components"), media.path("schema"), body);
        if (!errors.isEmpty()) {
            throw new AssertionError(what + ": body does not match the contract: " + errors);
        }
    }

    /**
     * The errors of a JSON body against a schema, which may $ref the given components (the schema
     * with the components next to it, so that its $refs resolve).
     */
    static List<Error> validate(JsonNode components, JsonNode schema, String body) {
        ObjectNode root = JSON.createObjectNode();
        root.set("components", components);
        root.putArray("allOf").add(schema);
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(JSON.writeValueAsString(root), InputFormat.JSON)
                .validate(body, InputFormat.JSON);
    }
}
