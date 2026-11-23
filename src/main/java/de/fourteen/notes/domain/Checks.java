package de.fourteen.notes.domain;

/**
 * The checks of a pull request: `build`, `e2e`, `review` and `acceptance` are required, merging
 * needs all green. `preview` is the deploy of the preview app.
 */
public record Checks(
        CheckState build,
        CheckState e2e,
        CheckState review,
        CheckState acceptance,
        CheckState preview) {

    public boolean green() {
        return build == CheckState.SUCCESS
                && e2e == CheckState.SUCCESS
                && review == CheckState.SUCCESS
                && acceptance == CheckState.SUCCESS;
    }

    public boolean pending() {
        return build == CheckState.PENDING
                || e2e == CheckState.PENDING
                || review == CheckState.PENDING
                || acceptance == CheckState.PENDING;
    }
}
