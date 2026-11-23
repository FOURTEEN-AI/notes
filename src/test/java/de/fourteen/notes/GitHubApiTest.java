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
import tools.jackson.databind.json.JsonMapper;

/**
 * Ideas and preview bar against a fake GitHub API. This app plays the preview of pull request 7.
 */
@SpringBootTest(
        properties = {
            "spring.security.user.name=test",
            "spring.security.user.password=secret",
            "github.token=test-token",
            "pull-request=7",
            "pull-request-sha=abc123",
            "notes.report-data=classpath*:jgiven-test/*.js"
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
    void submitted_idea_becomes_an_issue_with_the_label_refine() throws Exception {
        github.expect(requestTo(REPO + "/issues"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(
                        content()
                                .json(
                                        """
                                        {"title": "Edit notes", "body": "Click a note to edit it.", "labels": ["refine"]}
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
                .andExpect(jsonPath("$.description").value("Click a note to edit it."))
                .andExpect(jsonPath("$.status").value("REFINING"));
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
        github.expect(requestTo(REPO + "/issues?state=open&per_page=100"))
                .andRespond(
                        json(
                                """
                                [
                                  {"number": 19, "title": "Export notes", "labels": [{"name": "refine"}]},
                                  {"number": 18, "title": "Tag notes", "labels": [{"name": "refine"}]},
                                  {"number": 16, "title": "Dark mode", "body": "Add a dark mode.", "labels": [{"name": "agent"}]},
                                  {"number": 14, "title": "Search notes", "labels": [{"name": "agent"}]},
                                  {"number": 12, "title": "Edit notes", "labels": [{"name": "agent"}]},
                                  {"number": 10, "title": "Not an idea", "labels": [{"name": "bug"}]},
                                  {"number": 11, "title": "A pull request", "labels": [{"name": "agent"}], "pull_request": {}}
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
        github.expect(requestTo(REPO + "/actions/workflows/refine.yml/runs?per_page=30"))
                .andRespond(
                        json(
                                """
                                {"workflow_runs": [
                                  {"display_title": "Refine #19", "status": "queued"},
                                  {"display_title": "Refine #18", "status": "completed"}
                                ]}
                                """));
        github.expect(requestTo(REPO + "/commits/xyz/check-runs?per_page=100"))
                .andRespond(json(checkRuns("success", null, null, null)));
        github.expect(requestTo(REPO + "/commits/abc/check-runs?per_page=100"))
                .andRespond(json(checkRuns("success", "success")));

        mvc.perform(get("/api/ideas").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].number").value(19))
                .andExpect(jsonPath("$[0].status").value("REFINING"))
                .andExpect(jsonPath("$[0].previewUrl").doesNotExist())
                .andExpect(jsonPath("$[1].number").value(18))
                .andExpect(jsonPath("$[1].status").value("WAITING_FOR_ANSWER"))
                .andExpect(jsonPath("$[2].number").value(16))
                .andExpect(jsonPath("$[2].description").value("Add a dark mode."))
                .andExpect(jsonPath("$[3].description").value(""))
                .andExpect(jsonPath("$[2].status").value("REVIEW_RUNNING"))
                .andExpect(
                        jsonPath("$[2].previewUrl")
                                .value("https://notes-fourteen-it-issue-16.fly.dev"))
                .andExpect(jsonPath("$[2].previewReady").value(false))
                .andExpect(jsonPath("$[3].number").value(14))
                .andExpect(jsonPath("$[3].status").value("AGENT_WORKING"))
                .andExpect(jsonPath("$[3].previewUrl").doesNotExist())
                .andExpect(jsonPath("$[3].previewReady").value(false))
                .andExpect(jsonPath("$[4].number").value(12))
                .andExpect(jsonPath("$[4].status").value("READY_FOR_DEMO"))
                .andExpect(
                        jsonPath("$[4].previewUrl")
                                .value("https://notes-fourteen-it-issue-12.fly.dev"))
                .andExpect(jsonPath("$[4].previewReady").value(true));
    }

    @Test
    void chat_shows_only_the_messages_with_a_marker() throws Exception {
        github.expect(requestTo(REPO + "/issues/19/comments?per_page=100"))
                .andRespond(
                        json(
                                """
                                [
                                  {"body": "## Idea chat\\n\\nOnly as CSV."},
                                  {"body": "Claude is working on it"},
                                  {"body": "## Refine\\n\\nWhich columns should it have?"}
                                ]
                                """));

        mvc.perform(get("/api/ideas/19/messages").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].author").value("me"))
                .andExpect(jsonPath("$[0].text").value("Only as CSV."))
                .andExpect(jsonPath("$[1].author").value("agent"))
                .andExpect(jsonPath("$[1].text").value("Which columns should it have?"));
    }

    @Test
    void reply_is_a_comment_that_starts_the_refine_agent() throws Exception {
        github.expect(requestTo(REPO + "/issues/19/comments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"body\": \"## Idea chat\\n\\nTitle and text.\"}"))
                .andRespond(withStatus(HttpStatus.CREATED));

        mvc.perform(
                        post("/api/ideas/19/messages")
                                .header(HttpHeaders.AUTHORIZATION, LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\": \" Title and text. \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author").value("me"));
    }

    @Test
    void empty_reply_is_rejected() throws Exception {
        github.expect(never(), requestTo(REPO + "/issues/19/comments"));

        mvc.perform(
                        post("/api/ideas/19/messages")
                                .header(HttpHeaders.AUTHORIZATION, LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"text\": \" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void approval_posts_the_last_proposal_and_swaps_the_label() throws Exception {
        github.expect(requestTo(REPO + "/issues/19/comments?per_page=100"))
                .andRespond(
                        json(
                                """
                                [
                                  {"body": "## Refine\\n\\n```gherkin\\nold\\n```"},
                                  {"body": "## Idea chat\\n\\nNo, as CSV."},
                                  {"body": "## Refine\\n\\nOkay:\\n```gherkin\\n# language: de\\nFunktionalität: Export\\n```"}
                                ]
                                """));
        github.expect(requestTo(REPO + "/issues/19/comments"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content()
                                .json(
                                        "{\"body\": \"## Acceptance criteria\\n\\n```gherkin\\n# language: de\\nFunktionalität: Export\\n```\"}"))
                .andRespond(withStatus(HttpStatus.CREATED));
        github.expect(requestTo(REPO + "/issues/19/labels"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"labels\": [\"agent\"]}"))
                .andRespond(withStatus(HttpStatus.OK));
        github.expect(requestTo(REPO + "/issues/19/labels/refine"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.OK));

        mvc.perform(post("/api/ideas/19/approval").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isNoContent());
    }

    @Test
    void changed_and_removed_scenarios_show_their_old_version_from_the_report() throws Exception {
        github.expect(requestTo(REPO + "/issues/19/comments?per_page=100"))
                .andRespond(
                        json(
                                """
                                [
                                  {"body": "## Refine\\n\\n```gherkin\\nSzenario: Neu\\n\\n@ersetzt(\\"Eine Notiz speichern\\")\\nSzenario: Geändert\\n\\n@ersetzt(\\"Unbekannt\\")\\nSzenario: X\\n```"}
                                ]
                                """));

        mvc.perform(
                        get("/api/ideas/19/previous-scenarios")
                                .header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Eine Notiz speichern"))
                .andExpect(
                        jsonPath("$[0].steps[0]")
                                .value("Wenn ich speichere die Notiz \"Milch kaufen\""))
                .andExpect(jsonPath("$[0].steps[1]").value("Dann die Liste zeigt die Notiz"))
                .andExpect(jsonPath("$[0].steps[2]").value("Und sie hat einen Zeitstempel"))
                .andExpect(jsonPath("$[0].steps.length()").value(3));
    }

    @Test
    void a_second_approval_of_the_same_proposal_is_refused() throws Exception {
        github.expect(requestTo(REPO + "/issues/19/comments?per_page=100"))
                .andRespond(
                        json(
                                """
                                [
                                  {"body": "## Refine\\n\\n```gherkin\\nSzenario: Neu\\n```"},
                                  {"body": "## Acceptance criteria\\n\\n```gherkin\\nSzenario: Neu\\n```"}
                                ]
                                """));

        mvc.perform(post("/api/ideas/19/approval").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isConflict());
    }

    @Test
    void approval_without_a_proposal_is_refused() throws Exception {
        github.expect(requestTo(REPO + "/issues/19/comments?per_page=100"))
                .andRespond(json("[{\"body\": \"## Refine\\n\\nWhich columns?\"}]"));

        mvc.perform(post("/api/ideas/19/approval").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isConflict());
    }

    @Test
    void preview_shows_its_pull_request_and_checks() throws Exception {
        github.expect(requestTo(REPO + "/pulls/7"))
                .andRespond(json(pullRequest(7, "agent/issue-6", "def", "open")));
        github.expect(requestTo(REPO + "/commits/def/check-runs?per_page=100"))
                .andRespond(json(checkRuns("success", null)));
        issueComments(6, "[]");
        mvc.perform(get("/api/preview").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(7))
                .andExpect(jsonPath("$.title").value("feat: pull request 7"))
                .andExpect(jsonPath("$.deployedSha").value("abc123"))
                .andExpect(jsonPath("$.build").value("SUCCESS"))
                .andExpect(jsonPath("$.review").value("PENDING"))
                .andExpect(jsonPath("$.acceptance").value("SUCCESS"))
                .andExpect(jsonPath("$.mergeable").value(false))
                .andExpect(jsonPath("$.demo.length()").value(0));
    }

    @Test
    void preview_offers_an_amended_delta_that_the_coding_agent_proposed() throws Exception {
        github.expect(requestTo(REPO + "/pulls/7"))
                .andRespond(json(pullRequest(7, "agent/issue-6", "def", "open")));
        github.expect(requestTo(REPO + "/commits/def/check-runs?per_page=100"))
                .andRespond(json(checkRuns("success", "success")));
        issueComments(
                6,
                """
                [
                  {"body": "## Acceptance criteria\\n\\n```gherkin\\nold\\n```"},
                  {"body": "## Refine\\n\\nMain moved:\\n```gherkin\\n# language: de\\nnew\\n```"}
                ]
                """);

        mvc.perform(get("/api/preview").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(jsonPath("$.issue").value(6))
                .andExpect(jsonPath("$.proposal").value("# language: de\nnew"));
    }

    @Test
    void preview_offers_nothing_once_the_proposal_is_approved() throws Exception {
        github.expect(requestTo(REPO + "/pulls/7"))
                .andRespond(json(pullRequest(7, "agent/issue-6", "def", "open")));
        github.expect(requestTo(REPO + "/commits/def/check-runs?per_page=100"))
                .andRespond(json(checkRuns("success", "success")));
        issueComments(
                6,
                """
                [
                  {"body": "## Refine\\n\\n```gherkin\\nnew\\n```"},
                  {"body": "## Acceptance criteria\\n\\n```gherkin\\nnew\\n```"}
                ]
                """);

        mvc.perform(get("/api/preview").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(jsonPath("$.issue").value(6))
                .andExpect(jsonPath("$.proposal").doesNotExist());
    }

    @Test
    void preview_returns_the_demo_steps_from_the_pull_request_description() throws Exception {
        String body =
                """
                Adds editing.

                ```demo
                [
                  {"do": "Write a note and save it", "expect": "It is at the top", "selector": "main form textarea"},
                  {"do": "Reload the page", "expect": "The note is still there"},
                  {"expect": "A step without an action is left out"}
                ]
                ```

                Closes #6
                """;
        github.expect(requestTo(REPO + "/pulls/7"))
                .andRespond(json(withBody(pullRequest(7, "agent/issue-6", "def", "open"), body)));
        github.expect(requestTo(REPO + "/commits/def/check-runs?per_page=100"))
                .andRespond(json(checkRuns("success", "success")));
        issueComments(6, "[]");
        mvc.perform(get("/api/preview").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(jsonPath("$.demo.length()").value(2))
                .andExpect(jsonPath("$.demo[0].do").value("Write a note and save it"))
                .andExpect(jsonPath("$.demo[0].expect").value("It is at the top"))
                .andExpect(jsonPath("$.demo[0].selector").value("main form textarea"))
                .andExpect(jsonPath("$.demo[1].do").value("Reload the page"))
                .andExpect(jsonPath("$.demo[1].selector").doesNotExist());
    }

    @Test
    void broken_demo_block_means_no_tour() throws Exception {
        String body = "```demo\n[{\"do\": \"Click\",]\n```\n";
        github.expect(requestTo(REPO + "/pulls/7"))
                .andRespond(json(withBody(pullRequest(7, "agent/issue-6", "def", "open"), body)));
        github.expect(requestTo(REPO + "/commits/def/check-runs?per_page=100"))
                .andRespond(json(checkRuns("success", "success")));
        issueComments(6, "[]");
        mvc.perform(get("/api/preview").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo.length()").value(0));
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
    void merge_is_refused_unless_build_review_and_acceptance_are_green() throws Exception {
        github.expect(requestTo(REPO + "/pulls/7"))
                .andRespond(json(pullRequest(7, "agent/issue-6", "def", "open")));
        github.expect(requestTo(REPO + "/commits/def/check-runs?per_page=100"))
                .andRespond(json(checkRuns("success", "failure")));
        github.expect(never(), requestTo(REPO + "/pulls/7/merge"));

        mvc.perform(post("/api/preview/merge").header(HttpHeaders.AUTHORIZATION, LOGIN))
                .andExpect(status().isConflict());
    }

    @Test
    void merge_is_refused_while_acceptance_is_not_green() throws Exception {
        github.expect(requestTo(REPO + "/pulls/7"))
                .andRespond(json(pullRequest(7, "agent/issue-6", "def", "open")));
        github.expect(requestTo(REPO + "/commits/def/check-runs?per_page=100"))
                .andRespond(json(checkRuns("success", "success", "failure", "success")));
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

    private void issueComments(int issue, String comments) {
        github.expect(requestTo(REPO + "/issues/" + issue + "/comments?per_page=100"))
                .andRespond(json(comments));
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

    private static String withBody(String pullRequest, String body) {
        return pullRequest.replace(
                "\"state\"",
                "\"body\": " + JsonMapper.shared().writeValueAsString(body) + ", \"state\"");
    }

    private static String checkRuns(String build, String review) {
        return checkRuns(build, review, "success", "success");
    }

    /** An older failed review and the latest runs; a null conclusion means still running. */
    private static String checkRuns(
            String build, String review, String acceptance, String preview) {
        return """
                {"check_runs": [
                  {"id": 1, "name": "review", "status": "completed", "conclusion": "failure"},
                  {"id": 2, "name": "build", "status": "completed", "conclusion": "%s"},
                  %s,
                  %s,
                  %s
                ]}
                """
                .formatted(
                        build,
                        checkRun(3, "review", review),
                        checkRun(5, "acceptance", acceptance),
                        checkRun(4, "preview", preview));
    }

    private static String checkRun(int id, String name, String conclusion) {
        return conclusion == null
                ? "{\"id\": %d, \"name\": \"%s\", \"status\": \"in_progress\", \"conclusion\": null}"
                        .formatted(id, name)
                : "{\"id\": %d, \"name\": \"%s\", \"status\": \"completed\", \"conclusion\": \"%s\"}"
                        .formatted(id, name, conclusion);
    }
}
