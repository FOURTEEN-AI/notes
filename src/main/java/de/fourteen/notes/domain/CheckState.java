package de.fourteen.notes.domain;

/** The state of one check run, e.g. `build` or `review`, on the head of a pull request. */
public enum CheckState {
    PENDING,
    SUCCESS,
    FAILURE
}
