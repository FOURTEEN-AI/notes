package de.fourteen.notes.domain;

import java.util.List;

/** A scenario of the acceptance report: its title and its steps as Gherkin lines. */
public record Scenario(String title, List<String> steps) {}
