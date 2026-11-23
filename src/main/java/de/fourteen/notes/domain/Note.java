package de.fourteen.notes.domain;

import java.time.OffsetDateTime;

public record Note(long id, String text, OffsetDateTime createdAt) {}
