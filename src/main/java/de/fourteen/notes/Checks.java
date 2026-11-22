package de.fourteen.notes;

/** The two required checks of a pull request. Merging needs both green. */
record Checks(CheckState build, CheckState review) {

    boolean green() {
        return build == CheckState.SUCCESS && review == CheckState.SUCCESS;
    }

    boolean pending() {
        return build == CheckState.PENDING || review == CheckState.PENDING;
    }
}
