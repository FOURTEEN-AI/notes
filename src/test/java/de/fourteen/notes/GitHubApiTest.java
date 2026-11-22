package de.fourteen.notes;

import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;

/** Ideas and preview bar against a fake GitHub API. This app plays the preview of pull request 7. */
@SpringBootTest(
        properties = {
            "spring.security.user.name=test",
            "spring.security.user.password=secret",
            "github.token=test-token",
            "pull-request=7"
        })
@AutoConfigureMockMvc
@AutoConfigureMockRestServiceServer
@Import(TestcontainersConfiguration.class)
class GitHubApiTest {

    private static final String LOGIN =
            "Basic "
                    + Base64.getEncoder()
                            .encodeToString("test:secret".getBytes(StandardCharsets.UTF_8));
    private static final String REPO = "https://api.github.com/repos/FOURTEEN-AI/notes";

    @Autowired private MockMvc mvc;
    @Autowired private MockRestServiceServer github;

    @AfterEach
    void verify() {
        github.verify();
    }

    @Test
    void submitted_idea_becomes_an_issue_with_the_label_agent() throws Exception {
        github.expect(requestTo(REPO + "/issues"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(
                        content()
                                .json(
                                        """
                                        {"title": "Edit notes", "body": "Click a note to edit it.", "labels": ["agent"]}
                                        """))
                .andRespond(
                        withSuccess(
                                """
                                {"number": 12, "title": "Edit notes"}
                                """,
                                MediaType.APPLICATION_JSON));

        mvc.perform(
                        post("/api/ideas")
                                .header(HttpHeaders.AUTHORIZATION, LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"title": " Edit notes ", "description": "Click a note to edit it."}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value(12))
                .andExpect(jsonPath("$.status").value("AGENT_WORKING"));
    }

    @Test
    void idea_without_title_is_rejected() throws Exception {
        github.expect(never(), requestTo(REPO + "/issues"));

        mvc.perform(
                        post("/api/ideas")
                                .header(HttpHeaders.AUTHORIZATION, LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\": \" \", \"description\": \"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ideas_show_their_status_and_preview() throws Exception {
        github.expect(requestTo(REPO + "/issues?labels=agent&state=open&per_page=100"))
                .andRespond(
                        json(
                                """
                                [
                                  {"number": 16, "title": "Dark mode"},
                                  {"number": 14, "title": "Search notes"},
                                  {"number": 12, "title": "Edit notes"},
                                  {"number": 11, "title": "A pull request", "pull_request": {}}
                                ]
                                """));
        github.expect(requestTo(REPO + "/pulls?state=open&per_page=100"))
                .andRespond(
                        json(
                                "["
                                        + pullRequest(17, "agent/issue-16", "xyz", "open")
                                        + ","
                                        + pullRequest(13, "agent/issue-12", "abc", "open")
                                        + "]"));
        github.expect(requestTo(REPO + "/actions/workflows/agent.yml/runs?per_page=30"))
                .andRespond(
                        json(
                                """
                                {"workflow_runs": [
                                  {"display_title": "Agent #14", "status": "in_progress"},
                                  {"display_title": "Agent #13", "status": "completed"}
                                ]}
                                """));
        github.expect(requestTo(REPO + "/commits/xyz/check-runs?per_page=100"))
                .andRespond(json(checkRuns("success", null, null)));
        github.expect(requestTo(REPO + "/commits/abc/check-runs?per_page=100"))
                .andRespond(json(checkRuns("success", "success")));

        mvc.perform(get("/api/ideas").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].number").value(16))
                .andExpect(jsonPath("$[0].status").value("REVIEW_RUNNING"))
                .andExpect(
                        jsonPath("$[0].previewUrl").value("https://notes-fourteen-it-pr-17.fly.dev"))
                .andExpect(jsonPath("$[0].previewReady").value(false))
                .andExpect(jsonPath("$[1].number").value(14))
                .andExpect(jsonPath("$[1].status").value("AGENT_WORKING"))
                .andExpect(jsonPath("$[1].previewUrl").doesNotExist())
                .andExpect(jsonPath("$[1].previewReady").value(false))
                .andExpect(jsonPath("$[2].number").value(12))
                .andExpect(jsonPath("$[2].status").value("READY_FOR_DEMO"))
                .andExpect(
                        jsonPath("$[2].previewUrl").value("https://notes-fourteen-it-pr-13.fly.dev"))
                .andExpect(jsonPath("$[2].previewReady").value(true));
    }

    @Test
    void preview_shows_its_pull_request_and_checks() throws Exception {
        github.expect(requestTo(REPO + "/pulls/7"))
                .andRespond(json(pullRequest(7, "agent/issue-6", "def", "open")));
        github.expect(requestTo(REPO + "/commits/def/check-runs?per_page=100"))
                .andRespond(json(checkRuns("success", null)));

        mvc.perform(get("/api/preview").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(7))
                .andExpect(jsonPath("$.title").value("feat: pull request 7"))
                .andExpect(jsonPath("$.build").value("SUCCESS"))
                .andExpect(jsonPath("$.review").value("PENDING"))
                .andExpect(jsonPath("$.mergeable").value(false));
    }

    @Test
    void merged_preview_is_shown_as_merged_and_not_mergeable() throws Exception {
        github.expect(requestTo(REPO + "/pulls/7"))
                .andRespond(
                        json(
                                pullRequest(7, "agent/issue-6", "def", "closed")
                                        .replace("\"state\"", "\"merged\": true, \"state\"")));
        github.expect(requestTo(REPO + "/commits/def/check-runs?per_page=100"))
                .andRespond(json(checkRuns("success", "success")));

        mvc.perform(get("/api/preview").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(jsonPath("$.state").value("merged"))
                .andExpect(jsonPath("$.mergeable").value(false));
    }

    @Test
    void feedback_is_posted_on_the_pull_request_with_the_marker() throws Exception {
        github.expect(requestTo(REPO + "/issues/7/comments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content()
                                .json(
                                        """
                                        {"body": "## Human feedback\\n\\nMake the button red."}
                                        """))
                .andRespond(withStatus(HttpStatus.CREATED));

        mvc.perform(
                        post("/api/preview/feedback")
                                .header(HttpHeaders.AUTHORIZATION, LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\": \"Make the button red.\\n\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void merge_with_green_checks_squashes_exactly_the_checked_commit() throws Exception {
        github.expect(requestTo(REPO + "/pulls/7"))
                .andRespond(json(pullRequest(7, "agent/issue-6", "def", "open")));
        github.expect(requestTo(REPO + "/commits/def/check-runs?per_page=100"))
                .andRespond(json(checkRuns("success", "success")));
        github.expect(requestTo(REPO + "/pulls/7/merge"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"sha\": \"def\", \"merge_method\": \"squash\"}"))
                .andRespond(json("{\"merged\": true}"));

        mvc.perform(post("/api/preview/merge").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isNoContent());
    }

    @Test
    void merge_is_refused_unless_build_and_review_are_green() throws Exception {
        github.expect(requestTo(REPO + "/pulls/7"))
                .andRespond(json(pullRequest(7, "agent/issue-6", "def", "open")));
        github.expect(requestTo(REPO + "/commits/def/check-runs?per_page=100"))
                .andRespond(json(checkRuns("success", "failure")));
        github.expect(never(), requestTo(REPO + "/pulls/7/merge"));

        mvc.perform(post("/api/preview/merge").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isConflict());
    }

    @Test
    void github_errors_are_not_passed_on() throws Exception {
        github.expect(requestTo(REPO + "/pulls/7"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("Bad credentials"));

        mvc.perform(get("/api/preview").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isBadGateway());
    }

    private static org.springframework.test.web.client.ResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }

    private static String pullRequest(int number, String branch, String sha, String state) {
        return """
                {"number": %d, "title": "feat: pull request %d", "state": "%s",
                 "html_url": "https://github.com/FOURTEEN-AI/notes/pull/%d",
                 "head": {"ref": "%s", "sha": "%s"}}
                """
                .formatted(number, number, state, number, branch, sha);
    }

    private static String checkRuns(String build, String review) {
        return checkRuns(build, review, "success");
    }

    /** An older failed review and the latest runs; a null conclusion means still running. */
    private static String checkRuns(String build, String review, String preview) {
        return """
                {"check_runs": [
                  {"id": 1, "name": "review", "status": "completed", "conclusion": "failure"},
                  {"id": 2, "name": "build", "status": "completed", "conclusion": "%s"},
                  %s,
                  %s
                ]}
                """
                .formatted(build, checkRun(3, "review", review), checkRun(4, "preview", preview));
    }

    private static String checkRun(int id, String name, String conclusion) {
        return conclusion == null
                ? "{\"id\": %d, \"name\": \"%s\", \"status\": \"in_progress\", \"conclusion\": null}"
                        .formatted(id, name)
                : "{\"id\": %d, \"name\": \"%s\", \"status\": \"completed\", \"conclusion\": \"%s\"}"
                        .formatted(id, name, conclusion);
    }
}
