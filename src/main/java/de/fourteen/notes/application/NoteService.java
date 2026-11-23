package de.fourteen.notes.application;

import de.fourteen.notes.domain.DomainException;
import de.fourteen.notes.domain.Note;
import de.fourteen.notes.domain.NoteRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NoteService {

    static final int MAX_LENGTH = 280;

    private final NoteRepository notes;

    public NoteService(NoteRepository notes) {
        this.notes = notes;
    }

    public List<Note> list() {
        return notes.newestFirst();
    }

    public Note create(String text) {
        if (text == null || text.isBlank()) {
            throw new DomainException(DomainException.Kind.INVALID, "text must not be blank");
        }
        if (text.strip().length() > MAX_LENGTH) {
            throw new DomainException(
                    DomainException.Kind.TOO_LONG,
                    "Die Notiz ist zu lang (höchstens " + MAX_LENGTH + " Zeichen).");
        }
        return notes.add(text);
    }

    public void delete(long id) {
        if (!notes.remove(id)) {
            throw new DomainException(DomainException.Kind.NOT_FOUND, "note not found");
        }
    }
}
