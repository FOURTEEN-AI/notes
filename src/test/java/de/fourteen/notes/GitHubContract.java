package de.fourteen.notes;

import com.networknt.schema.Error;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The GitHub REST API as GitHub describes it, in the tests. Every request {@code GitHubClient}
 * sends and every response it gets, from a fake (Spring's MockRestServiceServer in the API tests,
 * the stub of the end-to-end tests in {@link GitHubStubContractTest}), is checked against {@code
 * config/github-openapi/api.github.com.json}: the official description, trimmed to the operations
 * the client uses (see {@code trim.mjs} there). Import it next to the other configuration of the
 * test.
 *
 * <p>A request must be an operation of the description: path, the query parameters (declared, and
 * from the enum if there is one) and the body, which must match its schema completely. A response
 * must have a status the operation describes and, if it has a JSON body, match the schema of that
 * status. The fakes are partial on purpose (a few fields of an issue, not the forty of GitHub), so
 * for responses {@code required} is not enforced; what a fake does say must be right: types, enums
 * and the names of the properties (an unknown one fails).
 *
 * <p>The description is OpenAPI 3.0: {@code nullable} is turned into JSON Schema before the
 * validation with networknt, like in {@link OpenApiContract}.
 */
@TestConfiguration(proxyBeanMethods = false)
class GitHubContract {

    private static final Path FILE = Path.of("config", "github-openapi", "api.github.com.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    /** The operations (method and path template) that were called, to see that a test did call. */
    static final class Exchanges {
        private final Set<String> operations = ConcurrentHashMap.newKeySet();

        Set<String> operations() {
            return operations;
        }
    }

    @Bean
    Exchanges gitHubExchanges() {
        return new Exchanges();
    }

    @Bean
    RestClientCustomizer requestsAndResponsesMatchTheContract(Exchanges exchanges) {
        JsonNode contract = JSON.readTree(read());
        return builder ->
                builder.requestInterceptor(
                        (request, body, execution) ->
                                exchange(contract, exchanges, request, body, execution));
    }

    private static String read() {
        try {
            return Files.readString(FILE);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ClientHttpResponse exchange(
            JsonNode contract,
            Exchanges exchanges,
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution)
            throws IOException {
        String method = request.getMethod().name().toLowerCase(Locale.ROOT);
        URI uri = request.getURI();
        String what = method.toUpperCase(Locale.ROOT) + " " + uri.getPath();

        String template = null;
        JsonNode operation = null;
        for (var item : contract.path("paths").properties()) {
            Pattern pattern = Pattern.compile(item.getKey().replaceAll("\\{[^}]+}", "[^/]+"));
            if (pattern.matcher(uri.getPath()).matches() && item.getValue().has(method)) {
                template = item.getKey();
                operation = item.getValue().get(method);
            }
        }
        if (operation == null) throw new AssertionError(what + ": not in the GitHub API");
        exchanges.operations().add(method.toUpperCase(Locale.ROOT) + " " + template);

        checkQuery(contract, operation, what, uri);
        checkBody(contract, operation, what, new String(body, StandardCharsets.UTF_8));

        ClientHttpResponse response = execution.execute(request, body);
        byte[] answer = response.getBody().readAllBytes();
        int status = response.getStatusCode().value();
        checkResponse(
                contract, operation, what + " -> " + status, status, response.getHeaders(), answer);
        return new Buffered(response, answer);
    }

    private static void checkQuery(JsonNode contract, JsonNode operation, String what, URI uri) {
        if (uri.getRawQuery() == null) return;
        Map<String, JsonNode> declared = new HashMap<>();
        for (JsonNode parameter : operation.path("parameters").values()) {
            JsonNode resolved = resolve(contract, parameter);
            if ("query".equals(resolved.path("in").asString())) {
                declared.put(resolved.path("name").asString(), resolved.path("schema"));
            }
        }
        for (String pair : Pattern.compile("&").splitAsStream(uri.getRawQuery()).toList()) {
            String[] nameAndValue = Pattern.compile("=").split(pair, 2);
            String name = URLDecoder.decode(nameAndValue[0], StandardCharsets.UTF_8);
            String value =
                    nameAndValue.length < 2
                            ? ""
                            : URLDecoder.decode(nameAndValue[1], StandardCharsets.UTF_8);
            JsonNode schema = declared.get(name);
            if (schema == null) {
                throw new AssertionError(what + ": query parameter " + name + " not in the API");
            }
            JsonNode allowed = resolve(contract, schema).path("enum");
            if (allowed.isArray()
                    && allowed.values().stream().noneMatch(v -> value.equals(v.asString()))) {
                throw new AssertionError(
                        what + ": query parameter " + name + "=" + value + " not in " + allowed);
            }
        }
    }

    private static void checkBody(JsonNode contract, JsonNode operation, String what, String body) {
        if (body.isEmpty()) return;
        JsonNode schema =
                operation
                        .path("requestBody")
                        .path("content")
                        .path("application/json")
                        .path("schema");
        if (schema.isMissingNode())
            throw new AssertionError(what + ": sends a body the API does not take");
        List<Error> errors = validate(contract, schema, body, false);
        if (!errors.isEmpty()) {
            throw new AssertionError(what + ": request body does not match the API: " + errors);
        }
    }

    private static void checkResponse(
            JsonNode contract,
            JsonNode operation,
            String what,
            int status,
            HttpHeaders headers,
            byte[] body) {
        JsonNode responses = operation.path("responses");
        // GitHub answers 401 to a missing or wrong token on every operation, but the description
        // lists it for none.
        if (status == 401) return;
        JsonNode response =
                responses.has(String.valueOf(status))
                        ? responses.get(String.valueOf(status))
                        : responses.path("default");
        if (response.isMissingNode()) throw new AssertionError(what + ": status not in the API");
        if (body.length == 0) return;
        var type = headers.getContentType();
        if (type != null && !type.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            throw new AssertionError(what + ": content type " + type + " is not JSON");
        }
        JsonNode content = resolve(contract, response).path("content").path("application/json");
        if (content.isMissingNode()) {
            throw new AssertionError(what + ": the API describes no JSON body for this status");
        }
        List<Error> errors =
                validate(
                        contract,
                        content.path("schema"),
                        new String(body, StandardCharsets.UTF_8),
                        true);
        if (!errors.isEmpty()) {
            throw new AssertionError(what + ": response body does not match the API: " + errors);
        }
    }

    private static List<Error> validate(
            JsonNode contract, JsonNode schema, String body, boolean partial) {
        return OpenApiContract.validate(
                JSON.valueToTree(jsonSchema(contract.path("components"), partial)),
                jsonSchema(schema, partial),
                body);
    }

    private static JsonNode resolve(JsonNode contract, JsonNode node) {
        JsonNode ref = node.path("$ref");
        if (!ref.isString()) return node;
        JsonNode target = contract;
        for (String part :
                Pattern.compile("/").splitAsStream(ref.asString().substring(2)).toList()) {
            target = target.path(part);
        }
        return target;
    }

    /**
     * OpenAPI 3.0 schema to JSON Schema: {@code nullable: true} becomes the type {@code null} next
     * to the others, an object with {@code properties} is closed ({@code unevaluatedProperties:
     * false}), and with {@code partial} no property is required.
     */
    private static JsonNode jsonSchema(JsonNode node, boolean partial) {
        if (node.isArray()) {
            ArrayNode copy = JSON.createArrayNode();
            node.values().forEach(item -> copy.add(jsonSchema(item, partial)));
            return copy;
        }
        if (!node.isObject()) return node;
        ObjectNode copy = JSON.createObjectNode();
        for (var entry : node.properties()) {
            if (partial && "required".equals(entry.getKey()) && entry.getValue().isArray())
                continue;
            copy.set(entry.getKey(), jsonSchema(entry.getValue(), partial));
        }
        if (copy.path("properties").isObject()) copy.put("unevaluatedProperties", false);
        if (copy.path("nullable").asBoolean(false)) {
            copy.remove("nullable");
            JsonNode type = copy.path("type");
            if (type.isString()) {
                copy.putArray("type").add(type.asString()).add("null");
            } else {
                ObjectNode wrapper = JSON.createObjectNode();
                wrapper.putArray("anyOf")
                        .add(copy)
                        .add(JSON.createObjectNode().put("type", "null"));
                return wrapper;
            }
            if (copy.path("enum").isArray()) ((ArrayNode) copy.get("enum")).addNull();
        }
        return copy;
    }

    /** The response again, with the body that was read to check it. */
    private static final class Buffered implements ClientHttpResponse {
        private final ClientHttpResponse response;
        private final byte[] body;

        Buffered(ClientHttpResponse response, byte[] body) {
            this.response = response;
            this.body = body.clone();
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return response.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return response.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return response.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            response.close();
        }
    }
}
