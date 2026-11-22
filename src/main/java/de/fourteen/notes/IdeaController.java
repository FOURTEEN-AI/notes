package de.fourteen.notes;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Feature ideas: each one is a GitHub issue with the label `refine` (the refine agent asks back)
 * or `agent` (the coding agent works on it). The chat of an idea is its issue comments that start
 * with one of the two markers.
 */
@RestController
@RequestMapping("/api/ideas")
class IdeaController {

    record NewIdea(String title, String description) {}

    /**
     * The chat of an idea starts with its description. previewUrl is set once a pull request
     * exists; previewReady once its app is deployed.
     */
    record Idea(
            int number,
            String title,
            String description,
            IdeaStatus status,
            String previewUrl,
            boolean previewReady) {}

    /** author is "me" (the human) or "agent" (the refine agent). */
    record Message(String author, String text) {}

    record NewMessage(String text) {}

    // The refine agent starts on comments with the human marker (refine.yml) and writes the other.
    static final String HUMAN_MARKER = "## Idea chat";
    static final String REFINE_MARKER = "## Refine";

    // The preview app of a pull request, as ci.yml names it.
    private static final String PREVIEW_URL = "https://notes-fourteen-it-pr-%d.fly.dev";

    private final GitHub github;

    IdeaController(GitHub github) {
        this.github = github;
    }

    @GetMapping
    List<Idea> list() {
        List<GitHub.Issue> issues = github.openIdeaIssues();
        // The coding agent works on the branch agent/issue-<n>.
        Map<String, GitHub.PullRequest> pullRequests =
                github.openPullRequests().stream()
                        .collect(
                                Collectors.toMap(
                                        GitHub.PullRequest::branch,
                                        Function.identity(),
                                        (a, b) -> a));
        Set<Integer> agentRuns = github.activeAgentRuns();
        Set<Integer> refineRuns = github.activeRefineRuns();
        return issues.stream()
                .map(
                        issue -> {
                            if (issue.refining()) {
                                return new Idea(
                                        issue.number(),
                                        issue.title(),
                                        issue.body(),
                                        IdeaStatus.refine(refineRuns.contains(issue.number())),
                                        null,
                                        false);
                            }
                            GitHub.PullRequest pr = pullRequests.get("agent/issue-" + issue.number());
                            boolean running =
                                    agentRuns.contains(issue.number())
                                            || (pr != null && agentRuns.contains(pr.number()));
                            Checks checks = pr == null ? null : github.checks(pr.sha());
                            return new Idea(
                                    issue.number(),
                                    issue.title(),
                                    issue.body(),
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
        return new Idea(
                issue.number(), issue.title(), description, IdeaStatus.REFINING, null, false);
    }

    @GetMapping("/{number}/messages")
    List<Message> messages(@PathVariable int number) {
        return github.comments(number).stream()
                .map(comment -> toMessage(comment.body()))
                .filter(message -> message != null)
                .toList();
    }

    @PostMapping(value = "/{number}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    Message reply(@PathVariable int number, @RequestBody NewMessage message) {
        if (message.text() == null || message.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text must not be blank");
        }
        String text = message.text().strip();
        github.comment(number, HUMAN_MARKER + "\n\n" + text);
        return new Message("me", text);
    }

    /** Null for a comment that is not part of the chat. */
    private static Message toMessage(String body) {
        if (body.startsWith(HUMAN_MARKER)) {
            return new Message("me", body.substring(HUMAN_MARKER.length()).strip());
        }
        if (body.startsWith(REFINE_MARKER)) {
            return new Message("agent", body.substring(REFINE_MARKER.length()).strip());
        }
        return null;
    }
}
