package de.fourteen.notes.domain;

import java.util.List;

/** Where the notes are kept. */
public interface NoteRepository {

    /** All notes, newest first. */
    List<Note> newestFirst();

    Note add(String text);

    /** False if there is no note with this id. */
    boolean remove(long id);
}
