package de.fourteen.notes;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The bar of a preview app: the pull request that this app was deployed from (env PULL_REQUEST,
 * set by ci.yml), its checks, feedback and merge. Without PULL_REQUEST everything here is 404.
 */
@RestController
@RequestMapping("/api/preview")
class PreviewController {

    /**
     * deployedSha is the commit this app runs (env PULL_REQUEST_SHA, set by ci.yml). A page that
     * was loaded from another commit offers to reload.
     */
    record Preview(
            int number,
            String title,
            String state,
            String deployedSha,
            CheckState build,
            CheckState review,
            boolean mergeable,
            List<DemoStep> demo) {}

    /**
     * A step of the guided demo: what to do, what should happen, and optionally the element to
     * highlight. The coding agent writes them as a ```demo block in the pull request description.
     */
    record DemoStep(@JsonProperty("do") String action, String expect, String selector) {}

    record Feedback(String text) {}

    // The coding agent starts on comments with this marker (agent.yml).
    static final String FEEDBACK_MARKER = "## Human feedback";

    private static final Pattern DEMO_BLOCK =
            Pattern.compile("^```demo[ \\t]*\\R(.*?)^```", Pattern.DOTALL | Pattern.MULTILINE);

    private final GitHub github;
    private final Integer pullRequest;
    private final String deployedSha;
    private final JsonMapper json;

    PreviewController(
            GitHub github,
            JsonMapper json,
            @Value("${pull-request:#{null}}") Integer pullRequest,
            @Value("${pull-request-sha:#{null}}") String deployedSha) {
        this.github = github;
        this.pullRequest = pullRequest;
        this.deployedSha = deployedSha;
        this.json = json;
    }

    @GetMapping
    Preview get() {
        GitHub.PullRequest pr = github.pullRequest(number());
        Checks checks = github.checks(pr.sha());
        return new Preview(
                pr.number(),
                pr.title(),
                pr.state(),
                deployedSha,
                checks.build(),
                checks.review(),
                "open".equals(pr.state()) && checks.green(),
                demo(pr.body()));
    }

    /** The steps of the ```demo block. Without a valid block there is no tour, not an error. */
    private List<DemoStep> demo(String body) {
        Matcher block = DEMO_BLOCK.matcher(body);
        if (!block.find()) {
            return List.of();
        }
        try {
            return Arrays.stream(json.readValue(block.group(1), DemoStep[].class))
                    .filter(step -> step != null && step.action() != null && !step.action().isBlank())
                    .toList();
        } catch (JacksonException e) {
            return List.of();
        }
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
