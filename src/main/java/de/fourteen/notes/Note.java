package de.fourteen.notes;

import java.time.OffsetDateTime;

record Note(long id, String text, OffsetDateTime createdAt) {}
