package de.fourteen.notes;

/** The state of one check run, e.g. `build` or `review`, on the head of a pull request. */
enum CheckState {
    PENDING,
    SUCCESS,
    FAILURE
}
