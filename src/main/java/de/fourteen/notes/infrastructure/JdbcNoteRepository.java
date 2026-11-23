package de.fourteen.notes.infrastructure;

import de.fourteen.notes.domain.Note;
import de.fourteen.notes.domain.NoteRepository;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcNoteRepository implements NoteRepository {

    private final JdbcClient jdbc;

    JdbcNoteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Note> newestFirst() {
        return jdbc.sql("select id, text, created_at from note order by created_at desc, id desc")
                .query(Note.class)
                .list();
    }

    @Override
    public Note add(String text) {
        return jdbc.sql("insert into note (text) values (:text) returning id, text, created_at")
                .param("text", text)
                .query(Note.class)
                .single();
    }

    @Override
    public boolean remove(long id) {
        return jdbc.sql("delete from note where id = :id").param("id", id).update() > 0;
    }
}
