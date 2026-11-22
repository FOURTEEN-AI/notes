package de.fourteen.notes;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/**
 * The few calls to the GitHub REST API that the app needs. GitHub stays the source of truth: the
 * app stores nothing about issues or pull requests itself.
 */
@Component
class GitHub {

    record Issue(int number, String title) {}

    record PullRequest(int number, String title, String url, String branch, String sha, String state) {}

    // run-name of agent.yml: "Agent #<issue or pull request number>".
    private static final Pattern AGENT_RUN = Pattern.compile("^Agent #(\\d+)$");

    private final GitHubProperties properties;
    private final RestClient rest;

    GitHub(GitHubProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.rest =
                builder.baseUrl(properties.apiUrl() + "/repos/" + properties.repository())
                        .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                        .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                        .defaultHeaders(
                                headers -> {
                                    if (properties.configured()) {
                                        headers.setBearerAuth(properties.token());
                                    }
                                })
                        .build();
    }

    /** Creates an issue with the label `agent`, which starts the coding agent. */
    Issue createIssue(String title, String body) {
        JsonNode issue =
                call(
                        () ->
                                rest.post()
                                        .uri("/issues")
                                        .body(
                                                Map.of(
                                                        "title", title,
                                                        "body", body,
                                                        "labels", List.of("agent")))
                                        .retrieve()
                                        .body(JsonNode.class));
        return new Issue(issue.get("number").asInt(), issue.get("title").asString());
    }

    /** Open issues with the label `agent`, newest first. */
    List<Issue> openAgentIssues() {
        JsonNode items =
                call(
                        () ->
                                rest.get()
                                        .uri("/issues?labels=agent&state=open&per_page=100")
                                        .retrieve()
                                        .body(JsonNode.class));
        return items.values().stream()
                // The issues endpoint lists pull requests too.
                .filter(item -> !item.has("pull_request"))
                .map(item -> new Issue(item.get("number").asInt(), item.get("title").asString()))
                .toList();
    }

    List<PullRequest> openPullRequests() {
        JsonNode items =
                call(
                        () ->
                                rest.get()
                                        .uri("/pulls?state=open&per_page=100")
                                        .retrieve()
                                        .body(JsonNode.class));
        return items.values().stream().map(GitHub::toPullRequest).toList();
    }

    PullRequest pullRequest(int number) {
        return toPullRequest(
                call(
                        () ->
                                rest.get()
                                        .uri("/pulls/{number}", number)
                                        .retrieve()
                                        .body(JsonNode.class)));
    }

    /** The numbers (issue or pull request) for which the coding agent runs or is queued. */
    Set<Integer> activeAgentRuns() {
        JsonNode runs =
                call(
                        () ->
                                rest.get()
                                        .uri("/actions/workflows/agent.yml/runs?per_page=30")
                                        .retrieve()
                                        .body(JsonNode.class));
        Set<Integer> numbers = new HashSet<>();
        for (JsonNode run : runs.get("workflow_runs").values()) {
            Matcher matcher = AGENT_RUN.matcher(run.path("display_title").asString(""));
            if (!"completed".equals(run.path("status").asString()) && matcher.matches()) {
                numbers.add(Integer.parseInt(matcher.group(1)));
            }
        }
        return numbers;
    }

    /** The latest runs of the checks `build`, `review` and `preview` on a commit. */
    Checks checks(String sha) {
        JsonNode runs =
                call(
                        () ->
                                rest.get()
                                        .uri("/commits/{sha}/check-runs?per_page=100", sha)
                                        .retrieve()
                                        .body(JsonNode.class));
        return new Checks(
                checkState(runs, "build"), checkState(runs, "review"), checkState(runs, "preview"));
    }

    void comment(int number, String body) {
        call(
                () ->
                        rest.post()
                                .uri("/issues/{number}/comments", number)
                                .body(Map.of("body", body))
                                .retrieve()
                                .toBodilessEntity());
    }

    /** Squash-merges, but only if the head is still the given commit. */
    void merge(int number, String sha) {
        call(
                () ->
                        rest.put()
                                .uri("/pulls/{number}/merge", number)
                                .body(Map.of("sha", sha, "merge_method", "squash"))
                                .retrieve()
                                .toBodilessEntity());
    }

    private static CheckState checkState(JsonNode runs, String name) {
        // A check can run more than once on a commit (re-runs); the highest id is the latest.
        return runs.get("check_runs").values().stream()
                .filter(run -> name.equals(run.path("name").asString()))
                .max((a, b) -> Long.compare(a.get("id").asLong(), b.get("id").asLong()))
                .map(
                        run -> {
                            if (!"completed".equals(run.path("status").asString())) {
                                return CheckState.PENDING;
                            }
                            return "success".equals(run.path("conclusion").asString())
                                    ? CheckState.SUCCESS
                                    : CheckState.FAILURE;
                        })
                .orElse(CheckState.PENDING);
    }

    private static PullRequest toPullRequest(JsonNode pr) {
        return new PullRequest(
                pr.get("number").asInt(),
                pr.get("title").asString(),
                pr.get("html_url").asString(),
                pr.get("head").get("ref").asString(),
                pr.get("head").get("sha").asString(),
                pr.path("merged").asBoolean(false) ? "merged" : pr.get("state").asString());
    }

    private <T> T call(Supplier<T> request) {
        if (!properties.configured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "GitHub is not configured");
        }
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            // 405: not mergeable, 409: the head changed. Both mean "look again".
            if (e.getStatusCode().value() == 405 || e.getStatusCode().value() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "GitHub refused");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub request failed");
        } catch (RestClientException e) {
            // Never pass GitHub's response on: it is not meant for the browser.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub request failed");
        }
    }
}
