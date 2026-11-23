package de.fourteen.notes.domain;

import java.util.List;
import java.util.Set;

/**
 * The few things the app needs from GitHub. GitHub stays the source of truth: the app stores
 * nothing about issues or pull requests itself. Every call fails with a {@link DomainException}
 * of kind UNAVAILABLE without a token, CONFLICT if GitHub refuses, and UPSTREAM_FAILED otherwise.
 */
public interface GitHub {

    /** Creates an issue with the label `refine`, which starts the refine agent. */
    Issue createIssue(String title, String body);

    /** Open issues with the label `refine` or `agent`, newest first. */
    List<Issue> openIdeaIssues();

    List<PullRequest> openPullRequests();

    PullRequest pullRequest(int number);

    /** The numbers (issue or pull request) for which the coding agent runs or is queued. */
    Set<Integer> activeAgentRuns();

    /** The numbers of the issues for which the refine agent runs or is queued. */
    Set<Integer> activeRefineRuns();

    /** The comments of an issue, oldest first. */
    List<Comment> comments(int number);

    /** The latest runs of the checks `build`, `review` and `preview` on a commit. */
    Checks checks(String sha);

    void comment(int number, String body);

    /** Swaps the label `refine` of an idea for `agent`, which starts the coding agent. */
    void release(int number);

    /** Squash-merges, but only if the head is still the given commit. */
    void merge(int number, String sha);
}
