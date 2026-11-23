package de.fourteen.notes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fourteen.notes.domain.DomainException;
import de.fourteen.notes.domain.Note;
import de.fourteen.notes.domain.NoteRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoteServiceTest {

    private final NoteRepository repository = mock(NoteRepository.class);
    private final NoteService service = new NoteService(repository);
    private final Note note = new Note(1, "Milch", OffsetDateTime.now(ZoneOffset.UTC));

    @Test
    void lists_the_notes_of_the_repository() {
        when(repository.newestFirst()).thenReturn(List.of(note));

        assertThat(service.list()).containsExactly(note);
    }

    @Test
    void creates_a_note_with_text() {
        when(repository.add("Milch")).thenReturn(note);

        assertThat(service.create("Milch")).isEqualTo(note);
    }

    @Test
    void refuses_a_blank_or_missing_text() {
        for (String text : new String[] {null, "", "  \n"}) {
            assertThatThrownBy(() -> service.create(text))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            e -> assertThat(e.kind()).isEqualTo(DomainException.Kind.INVALID));
        }
    }

    @Test
    void deletes_a_note_and_complains_about_an_unknown_one() {
        when(repository.remove(1)).thenReturn(true);
        when(repository.remove(2)).thenReturn(false);

        service.delete(1);
        verify(repository).remove(1);
        assertThatThrownBy(() -> service.delete(2))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.kind()).isEqualTo(DomainException.Kind.NOT_FOUND));
    }
}
