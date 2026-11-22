package de.fourteen.notes;

/**
 * The checks of a pull request: `build` and `review` are required, merging needs both green.
 * `preview` is the deploy of the preview app.
 */
record Checks(CheckState build, CheckState review, CheckState preview) {

    boolean green() {
        return build == CheckState.SUCCESS && review == CheckState.SUCCESS;
    }

    boolean pending() {
        return build == CheckState.PENDING || review == CheckState.PENDING;
    }
}
