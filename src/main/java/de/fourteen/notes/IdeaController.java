package de.fourteen.notes;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Feature ideas: each one is a GitHub issue with the label `agent`. */
@RestController
@RequestMapping("/api/ideas")
class IdeaController {

    record NewIdea(String title, String description) {}

    /** previewUrl is set once a pull request exists; previewReady once its app is deployed. */
    record Idea(
            int number, String title, IdeaStatus status, String previewUrl, boolean previewReady) {}

    // The preview app of a pull request, as ci.yml names it.
    private static final String PREVIEW_URL = "https://notes-fourteen-it-pr-%d.fly.dev";

    private final GitHub github;

    IdeaController(GitHub github) {
        this.github = github;
    }

    @GetMapping
    List<Idea> list() {
        List<GitHub.Issue> issues = github.openAgentIssues();
        // The coding agent works on the branch agent/issue-<n>.
        Map<String, GitHub.PullRequest> pullRequests =
                github.openPullRequests().stream()
                        .collect(
                                Collectors.toMap(
                                        GitHub.PullRequest::branch,
                                        Function.identity(),
                                        (a, b) -> a));
        Set<Integer> agentRuns = github.activeAgentRuns();
        return issues.stream()
                .map(
                        issue -> {
                            GitHub.PullRequest pr = pullRequests.get("agent/issue-" + issue.number());
                            boolean running =
                                    agentRuns.contains(issue.number())
                                            || (pr != null && agentRuns.contains(pr.number()));
                            Checks checks = pr == null ? null : github.checks(pr.sha());
                            return new Idea(
                                    issue.number(),
                                    issue.title(),
                                    IdeaStatus.of(running, checks),
                                    pr == null ? null : PREVIEW_URL.formatted(pr.number()),
                                    checks != null && checks.preview() == CheckState.SUCCESS);
                        })
                .toList();
    }

    // JSON only, like all writes (see SecurityConfiguration).
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    Idea create(@RequestBody NewIdea idea) {
        if (idea.title() == null || idea.title().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title must not be blank");
        }
        String description = idea.description() == null ? "" : idea.description();
        GitHub.Issue issue = github.createIssue(idea.title().strip(), description);
        return new Idea(issue.number(), issue.title(), IdeaStatus.AGENT_WORKING, null, false);
    }
}
