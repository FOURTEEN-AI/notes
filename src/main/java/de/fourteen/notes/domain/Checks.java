package de.fourteen.notes.domain;

/**
 * The checks of a pull request: `build` and `review` are required, merging needs both green.
 * `preview` is the deploy of the preview app.
 */
public record Checks(CheckState build, CheckState review, CheckState preview) {

    public boolean green() {
        return build == CheckState.SUCCESS && review == CheckState.SUCCESS;
    }

    public boolean pending() {
        return build == CheckState.PENDING || review == CheckState.PENDING;
    }
}
