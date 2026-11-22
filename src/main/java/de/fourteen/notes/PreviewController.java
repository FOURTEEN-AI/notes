package de.fourteen.notes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The bar of a preview app: the pull request that this app was deployed from (env PULL_REQUEST,
 * set by ci.yml), its checks, feedback and merge. Without PULL_REQUEST everything here is 404.
 */
@RestController
@RequestMapping("/api/preview")
class PreviewController {

    record Preview(
            int number, String title, String state, CheckState build, CheckState review, boolean mergeable) {}

    record Feedback(String text) {}

    // The coding agent starts on comments with this marker (agent.yml).
    static final String FEEDBACK_MARKER = "## Human feedback";

    private final GitHub github;
    private final Integer pullRequest;

    PreviewController(GitHub github, @Value("${pull-request:#{null}}") Integer pullRequest) {
        this.github = github;
        this.pullRequest = pullRequest;
    }

    @GetMapping
    Preview get() {
        GitHub.PullRequest pr = github.pullRequest(number());
        Checks checks = github.checks(pr.sha());
        return new Preview(
                pr.number(),
                pr.title(),
                pr.state(),
                checks.build(),
                checks.review(),
                "open".equals(pr.state()) && checks.green());
    }

    @PostMapping(path = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    void feedback(@RequestBody Feedback feedback) {
        if (feedback.text() == null || feedback.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text must not be blank");
        }
        github.comment(number(), FEEDBACK_MARKER + "\n\n" + feedback.text().strip());
    }

    /**
     * The token belongs to an admin and could bypass the branch protection, so the app checks the
     * required checks itself, on exactly the commit it merges.
     */
    @PostMapping("/merge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void merge() {
        GitHub.PullRequest pr = github.pullRequest(number());
        if (!"open".equals(pr.state()) || !github.checks(pr.sha()).green()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "build and review must be green on an open pull request");
        }
        github.merge(pr.number(), pr.sha());
    }

    private int number() {
        if (pullRequest == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not a preview app");
        }
        return pullRequest;
    }
}
