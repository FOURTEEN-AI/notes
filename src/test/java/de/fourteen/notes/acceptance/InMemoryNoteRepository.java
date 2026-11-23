package de.fourteen.notes.acceptance;

import de.fourteen.notes.domain.Note;
import de.fourteen.notes.domain.NoteRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/** In-memory fake of the port, for the acceptance tests. */
class InMemoryNoteRepository implements NoteRepository {

    private final List<Note> notes = new ArrayList<>();
    private long nextId = 1;

    @Override
    public List<Note> newestFirst() {
        return notes.reversed();
    }

    @Override
    public Note add(String text) {
        Note note = new Note(nextId++, text, OffsetDateTime.now());
        notes.add(note);
        return note;
    }

    @Override
    public boolean remove(long id) {
        return notes.removeIf(note -> note.id() == id);
    }
}
