package de.fourteen.notes.domain;

/**
 * A feature idea: a GitHub issue with the label `refine` (the refine agent asks back) or `agent`
 * (the coding agent works on it). The chat of an idea starts with its description. previewUrl is
 * set once a pull request exists; previewReady once its app is deployed.
 */
public record Idea(
        int number,
        String title,
        String description,
        IdeaStatus status,
        String previewUrl,
        boolean previewReady) {}
