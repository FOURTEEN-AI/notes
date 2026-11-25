package de.fourteen.notes.domain;

/**
 * An idea on GitHub. refining: the label `refine` is still set, so the refine agent has not
 * released the idea.
 */
public record Issue(int number, String title, String body, boolean refining) {

    /** The coding agent works on this branch. */
    public String branch() {
        return "agent/issue-" + number;
    }

    /** The preview app of a pull request, as ci.yml names it. */
    public String previewUrl() {
        return "https://notes-fourteen-it-issue-%d.fly.dev".formatted(number);
    }
}
