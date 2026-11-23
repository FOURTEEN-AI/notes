package de.fourteen.notes.domain;

/** state is "open", "closed" or "merged". */
public record PullRequest(
        int number, String title, String body, String url, String branch, String sha, String state) {

    public boolean open() {
        return "open".equals(state);
    }
}
