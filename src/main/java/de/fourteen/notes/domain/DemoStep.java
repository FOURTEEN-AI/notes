package de.fourteen.notes.domain;

/**
 * A step of the guided demo: what to do, what should happen, and optionally the element to
 * highlight. The coding agent writes them as a ```demo block in the pull request description.
 */
public record DemoStep(String action, String expect, String selector) {}
