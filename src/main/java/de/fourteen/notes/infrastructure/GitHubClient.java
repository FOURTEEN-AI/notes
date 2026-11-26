package de.fourteen.notes.infrastructure;

import de.fourteen.notes.domain.CheckState;
import de.fourteen.notes.domain.Checks;
import de.fourteen.notes.domain.Comment;
import de.fourteen.notes.domain.DomainException;
import de.fourteen.notes.domain.GitHub;
import de.fourteen.notes.domain.Issue;
import de.fourteen.notes.domain.PullRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/** The port {@link GitHub} on the GitHub REST API. */
@Component
class GitHubClient implements GitHub {

    // run-name of agent.yml: "Agent #<issue or pull request number>", of refine.yml: "Refine
    // #<issue>".
    private static final Pattern AGENT_RUN = Pattern.compile("^Agent #(\\d+)$");
    private static final Pattern REFINE_RUN = Pattern.compile("^Refine #(\\d+)$");

    private final GitHubProperties properties;
    private final RestClient rest;

    GitHubClient(GitHubProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.rest =
                builder.baseUrl(properties.apiUrl() + "/repos/" + properties.repository())
                        .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                        .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                        .defaultHeaders(headers -> {
                            if (properties.configured()) {
                                headers.setBearerAuth(properties.token());
                            }
                        })
                        .build();
    }

    @Override
    public Issue createIssue(String title, String body) {
        JsonNode issue =
                call(() ->
                        rest.post()
                                .uri("/issues")
                                .body(
                                        Map.of(
                                                "title", title,
                                                "body", body,
                                                "labels", List.of("refine")))
                                .retrieve()
                                .body(JsonNode.class));
        return toIssue(issue);
    }

    @Override
    public List<Issue> openIdeaIssues() {
        JsonNode items =
                call(() ->
                        rest.get()
                                .uri("/issues?state=open&per_page=100")
                                .retrieve()
                                .body(JsonNode.class));
        return items.values().stream()
                // The issues endpoint lists pull requests too.
                .filter(item -> !item.has("pull_request"))
                .filter(item -> hasLabel(item, "refine") || hasLabel(item, "agent"))
                .map(GitHubClient::toIssue)
                .toList();
    }

    @Override
    public List<PullRequest> openPullRequests() {
        JsonNode items =
                call(() ->
                        rest.get()
                                .uri("/pulls?state=open&per_page=100")
                                .retrieve()
                                .body(JsonNode.class));
        return items.values().stream().map(GitHubClient::toPullRequest).toList();
    }

    @Override
    public PullRequest pullRequest(int number) {
        return toPullRequest(
                call(() ->
                        rest.get().uri("/pulls/{number}", number).retrieve().body(JsonNode.class)));
    }

    @Override
    public Set<Integer> activeAgentRuns() {
        return activeRuns("agent.yml", AGENT_RUN);
    }

    @Override
    public Set<Integer> activeRefineRuns() {
        return activeRuns("refine.yml", REFINE_RUN);
    }

    @Override
    public List<Comment> comments(int number) {
        JsonNode items =
                call(() ->
                        rest.get()
                                .uri("/issues/{number}/comments?per_page=100", number)
                                .retrieve()
                                .body(JsonNode.class));
        return items.values().stream()
                .map(item -> new Comment(item.path("body").asString("")))
                .toList();
    }

    private Set<Integer> activeRuns(String workflow, Pattern runName) {
        JsonNode runs =
                call(() ->
                        rest.get()
                                .uri("/actions/workflows/{workflow}/runs?per_page=30", workflow)
                                .retrieve()
                                .body(JsonNode.class));
        Set<Integer> numbers = new HashSet<>();
        for (JsonNode run : runs.get("workflow_runs").values()) {
            Matcher matcher = runName.matcher(run.path("display_title").asString(""));
            if (!"completed".equals(run.path("status").asString()) && matcher.matches()) {
                numbers.add(Integer.parseInt(matcher.group(1)));
            }
        }
        return numbers;
    }

    @Override
    public Checks checks(String sha) {
        JsonNode runs =
                call(() ->
                        rest.get()
                                .uri("/commits/{sha}/check-runs?per_page=100", sha)
                                .retrieve()
                                .body(JsonNode.class));
        return new Checks(
                checkState(runs, "build"),
                checkState(runs, "review"),
                checkState(runs, "acceptance"),
                checkState(runs, "preview"));
    }

    @Override
    public void comment(int number, String body) {
        call(() ->
                rest.post()
                        .uri("/issues/{number}/comments", number)
                        .body(Map.of("body", body))
                        .retrieve()
                        .toBodilessEntity());
    }

    @Override
    public void release(int number) {
        call(() ->
                rest.post()
                        .uri("/issues/{number}/labels", number)
                        .body(Map.of("labels", List.of("agent")))
                        .retrieve()
                        .toBodilessEntity());
        call(() ->
                rest.delete()
                        .uri("/issues/{number}/labels/refine", number)
                        .retrieve()
                        .toBodilessEntity());
    }

    @Override
    public void merge(int number, String sha) {
        call(() ->
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
                .map(run -> {
                    if (!"completed".equals(run.path("status").asString())) {
                        return CheckState.PENDING;
                    }
                    return "success".equals(run.path("conclusion").asString())
                            ? CheckState.SUCCESS
                            : CheckState.FAILURE;
                })
                .orElse(CheckState.PENDING);
    }

    private static boolean hasLabel(JsonNode issue, String name) {
        return issue.path("labels").values().stream()
                .anyMatch(label -> name.equals(label.path("name").asString()));
    }

    private static Issue toIssue(JsonNode issue) {
        return new Issue(
                issue.get("number").asInt(),
                issue.get("title").asString(),
                // An empty description is null.
                issue.path("body").isString() ? issue.get("body").asString() : "",
                hasLabel(issue, "refine"));
    }

    private static PullRequest toPullRequest(JsonNode pr) {
        return new PullRequest(
                pr.get("number").asInt(),
                pr.get("title").asString(),
                // An empty description is null.
                pr.path("body").isString() ? pr.get("body").asString() : "",
                pr.get("html_url").asString(),
                pr.get("head").get("ref").asString(),
                pr.get("head").get("sha").asString(),
                pr.path("merged").asBoolean(false) ? "merged" : pr.get("state").asString());
    }

    private <T> T call(Supplier<T> request) {
        if (!properties.configured()) {
            throw new DomainException(DomainException.Kind.UNAVAILABLE, "GitHub is not configured");
        }
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            // 405: not mergeable, 409: the head changed. Both mean "look again".
            if (e.getStatusCode().value() == 405 || e.getStatusCode().value() == 409) {
                throw new DomainException(DomainException.Kind.CONFLICT, "GitHub refused");
            }
            throw new DomainException(
                    DomainException.Kind.UPSTREAM_FAILED, "GitHub request failed");
        } catch (RestClientException e) {
            // Never pass GitHub's response on: it is not meant for the browser.
            throw new DomainException(
                    DomainException.Kind.UPSTREAM_FAILED, "GitHub request failed");
        }
    }
}
