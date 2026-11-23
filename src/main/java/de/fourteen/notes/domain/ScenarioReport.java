package de.fourteen.notes.domain;

import java.util.Optional;

/** Port: the scenarios of the acceptance tests of the running build, which is {@code main}. */
public interface ScenarioReport {

    Optional<Scenario> find(String title);
}
