package de.fourteen.notes.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.fourteen.notes.domain.CheckState;
import de.fourteen.notes.domain.Checks;
import de.fourteen.notes.domain.DemoStep;
import de.fourteen.notes.domain.DomainException;
import de.fourteen.notes.domain.GitHub;
import de.fourteen.notes.domain.PullRequest;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The pull request that this preview app was deployed from (env PULL_REQUEST, set by ci.yml), its
 * checks, feedback and merge. Without PULL_REQUEST everything here is NOT_FOUND.
 */
@Service
public class PreviewService {

    /**
     * deployedSha is the commit this app runs (env PULL_REQUEST_SHA, set by ci.yml). A page that
     * was loaded from another commit offers to reload.
     */
    public record Preview(
            int number,
            String title,
            String state,
            String deployedSha,
            CheckState build,
            CheckState review,
            boolean mergeable,
            List<DemoStep> demo) {}

    /** A step as the coding agent writes it in the ```demo block (format in CLAUDE.md). */
    private record WrittenStep(@JsonProperty("do") String action, String expect, String selector) {}

    // The coding agent starts on comments with this marker (agent.yml).
    private static final String FEEDBACK_MARKER = "## Human feedback";

    private static final Pattern DEMO_BLOCK =
            Pattern.compile("^```demo[ \\t]*\\R(.*?)^```", Pattern.DOTALL | Pattern.MULTILINE);

    private final GitHub github;
    private final JsonMapper json;
    private final Integer pullRequest;
    private final String deployedSha;

    PreviewService(
            GitHub github,
            JsonMapper json,
            @Value("${pull-request:#{null}}") Integer pullRequest,
            @Value("${pull-request-sha:#{null}}") String deployedSha) {
        this.github = github;
        this.json = json;
        this.pullRequest = pullRequest;
        this.deployedSha = deployedSha;
    }

    public Preview get() {
        PullRequest pr = github.pullRequest(number());
        Checks checks = github.checks(pr.sha());
        return new Preview(
                pr.number(),
                pr.title(),
                pr.state(),
                deployedSha,
                checks.build(),
                checks.review(),
                pr.open() && checks.green(),
                demo(pr.body()));
    }

    public void feedback(String text) {
        if (text == null || text.isBlank()) {
            throw new DomainException(DomainException.Kind.INVALID, "text must not be blank");
        }
        github.comment(number(), FEEDBACK_MARKER + "\n\n" + text.strip());
    }

    /**
     * The token belongs to an admin and could bypass the branch protection, so the app checks the
     * required checks itself, on exactly the commit it merges.
     */
    public void merge() {
        PullRequest pr = github.pullRequest(number());
        if (!pr.open() || !github.checks(pr.sha()).green()) {
            throw new DomainException(
                    DomainException.Kind.CONFLICT,
                    "build and review must be green on an open pull request");
        }
        github.merge(pr.number(), pr.sha());
    }

    /** The steps of the ```demo block. Without a valid block there is no tour, not an error. */
    private List<DemoStep> demo(String body) {
        Matcher block = DEMO_BLOCK.matcher(body);
        if (!block.find()) {
            return List.of();
        }
        try {
            return Arrays.stream(json.readValue(block.group(1), WrittenStep[].class))
                    .filter(step -> step != null && step.action() != null && !step.action().isBlank())
                    .map(step -> new DemoStep(step.action(), step.expect(), step.selector()))
                    .toList();
        } catch (JacksonException e) {
            return List.of();
        }
    }

    private int number() {
        if (pullRequest == null) {
            throw new DomainException(DomainException.Kind.NOT_FOUND, "not a preview app");
        }
        return pullRequest;
    }
}
