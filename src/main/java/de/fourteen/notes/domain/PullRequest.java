package de.fourteen.notes.domain;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** state is "open", "closed" or "merged". */
public record PullRequest(
        int number, String title, String body, String url, String branch, String sha, String state) {

    private static final Pattern AGENT_BRANCH = Pattern.compile("agent/issue-(\\d+)");

    /** The idea this pull request implements, if it is the coding agent's branch. */
    public OptionalInt issue() {
        Matcher matcher = AGENT_BRANCH.matcher(branch == null ? "" : branch);
        return matcher.matches() ? OptionalInt.of(Integer.parseInt(matcher.group(1))) : OptionalInt.empty();
    }

    public boolean open() {
        return "open".equals(state);
    }
}
