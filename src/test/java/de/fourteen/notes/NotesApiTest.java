package de.fourteen.notes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// github.token is pinned empty: GitHub Actions sets the env GITHUB_TOKEN, which would bind to it.
@SpringBootTest(
        properties = {
            "spring.security.user.name=test",
            "spring.security.user.password=secret",
            "github.token="
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotesApiTest {

    private static final String LOGIN =
            "Basic "
                    + Base64.getEncoder()
                            .encodeToString("test:secret".getBytes(StandardCharsets.UTF_8));

    @Autowired private MockMvc mvc;

    @Test
    void saved_note_appears_first_in_the_list() throws Exception {
        mvc.perform(
                        post("/api/notes")
                                .header(HttpHeaders.AUTHORIZATION, LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\":\"first\"}"))
                .andExpect(status().isCreated());
        mvc.perform(
                        post("/api/notes")
                                .header(HttpHeaders.AUTHORIZATION, LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\":\"second\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("second"))
                .andExpect(jsonPath("$.createdAt").isString());

        mvc.perform(get("/api/notes").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("second"))
                .andExpect(jsonPath("$[1].text").value("first"));
    }

    @Test
    void deleted_note_disappears_and_unknown_id_is_not_found() throws Exception {
        String body =
                mvc.perform(
                                post("/api/notes")
                                        .header(HttpHeaders.AUTHORIZATION, LOGIN)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"text\":\"to delete\"}"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        long id = new tools.jackson.databind.json.JsonMapper().readTree(body).get("id").asLong();

        mvc.perform(delete("/api/notes/" + id).header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/notes").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
        mvc.perform(delete("/api/notes/" + id).header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void blank_note_is_rejected() throws Exception {
        mvc.perform(
                        post("/api/notes")
                                .header(HttpHeaders.AUTHORIZATION, LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void form_post_is_rejected() throws Exception {
        mvc.perform(
                        post("/api/notes")
                                .header(HttpHeaders.AUTHORIZATION, LOGIN)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .content("text=evil"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void notes_need_a_login() throws Exception {
        mvc.perform(get("/api/notes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/")).andExpect(status().isUnauthorized());
    }

    @Test
    void without_token_and_pull_request_ideas_and_preview_bar_are_off() throws Exception {
        mvc.perform(get("/api/ideas").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/preview").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isNoContent());
    }

    @Test
    void health_check_is_public() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void configuration_of_the_app_is_not_exposed() throws Exception {
        mvc.perform(get("/actuator/env").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isNotFound());
        mvc.perform(get("/actuator/configprops").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isNotFound());
    }
}
