package de.fourteen.notes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The API contract is checked in as docs/openapi.json. This test generates the description from the
 * controllers (springdoc) and fails when it differs from the file, so a changed endpoint needs a
 * changed contract in the same commit.
 */
@SpringBootTest(
        properties = {
            "spring.security.user.name=test",
            "spring.security.user.password=secret",
            "github.token=",
            // Keys sorted: the order of the controller methods is not stable across JVMs.
            "springdoc.writer-with-order-by-keys=true"
        })
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, OpenApiContract.class})
class OpenApiContractTest {

    private static final String LOGIN =
            "Basic "
                    + Base64.getEncoder()
                            .encodeToString("test:secret".getBytes(StandardCharsets.UTF_8));

    @Autowired private MockMvc mvc;

    @Test
    void checked_in_contract_equals_the_generated_one() throws Exception {
        String body =
                mvc.perform(get("/v3/api-docs").header(HttpHeaders.AUTHORIZATION, LOGIN))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        JsonNode generated = mapper.readTree(body);
        // Always written, so that an update is one copy: cp build/openapi.json docs/openapi.json
        Path out = Path.of("build", "openapi.json");
        Files.writeString(out, mapper.writeValueAsString(generated) + "\n");

        assertThat(Files.readString(OpenApiContract.FILE))
                .as("docs/openapi.json is out of date. Review %s and copy it over.", out)
                .isEqualTo(Files.readString(out));
    }
}
