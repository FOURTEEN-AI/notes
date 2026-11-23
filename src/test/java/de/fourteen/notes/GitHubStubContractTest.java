package de.fourteen.notes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The GitHub stub of the end-to-end tests (frontend/e2e/github-stub.mjs, started with Node) against
 * the real {@code GitHubClient}: every request of the client and every answer of the stub is
 * checked against the GitHub API by {@link GitHubContract}. The calls are those the end-to-end
 * tests make through the browser (ideas and chat, preview bar, feedback). The stub does not
 * implement releasing an idea or merging; the fakes of {@link GitHubApiTest} cover those.
 */
@SpringBootTest(
        properties = {
            "spring.security.user.name=test",
            "spring.security.user.password=secret",
            "github.token=test-token",
            "pull-request=7",
            "pull-request-sha=abc1234abc1234abc1234abc1234abc1234abc12",
            "notes.report-data=classpath*:jgiven-test/*.js"
        })
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, GitHubContract.class})
class GitHubStubContractTest {

    private static final String LOGIN =
            "Basic "
                    + Base64.getEncoder()
                            .encodeToString("test:secret".getBytes(StandardCharsets.UTF_8));

    private static Process stub;
    private static int port;

    @BeforeAll
    static void startTheStub() throws IOException {
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        }
        stub =
                new ProcessBuilder(
                                "node",
                                "--input-type=module",
                                "-e",
                                "import { startGitHubStub } from './frontend/e2e/github-stub.mjs';"
                                        + " await startGitHubStub("
                                        + port
                                        + "); console.log('ready');")
                        .redirectErrorStream(true)
                        .start();
        var output =
                new BufferedReader(
                        new InputStreamReader(stub.getInputStream(), StandardCharsets.UTF_8));
        assertThat(output.readLine()).as("output of the stub").isEqualTo("ready");
    }

    @AfterAll
    static void stopTheStub() {
        stub.destroy();
    }

    @DynamicPropertySource
    static void theStubIsGitHub(DynamicPropertyRegistry registry) {
        registry.add("github.api-url", () -> "http://127.0.0.1:" + port);
    }

    @Autowired private MockMvc mvc;
    @Autowired private GitHubContract.Exchanges exchanges;

    @Test
    void stub_answers_the_client_like_github() throws Exception {
        ok(get("/api/ideas"));
        ok(
                post("/api/ideas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Edit notes\", \"description\": \"Click one.\"}"));
        ok(get("/api/ideas/11/messages"));
        ok(
                post("/api/ideas/11/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"Dark blue.\"}"));
        ok(get("/api/preview"));
        ok(
                post("/api/preview/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"Make the button red.\"}"));

        // A call that the stub cannot answer is a 502 above; this shows that the stub was asked.
        assertThat(exchanges.operations())
                .contains(
                        "GET /repos/{owner}/{repo}/issues",
                        "POST /repos/{owner}/{repo}/issues",
                        "GET /repos/{owner}/{repo}/issues/{issue_number}/comments",
                        "POST /repos/{owner}/{repo}/issues/{issue_number}/comments",
                        "GET /repos/{owner}/{repo}/pulls/{pull_number}",
                        "GET /repos/{owner}/{repo}/commits/{ref}/check-runs");
    }

    private void ok(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request.header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().is2xxSuccessful());
    }
}
