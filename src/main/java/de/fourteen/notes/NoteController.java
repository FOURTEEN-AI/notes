package de.fourteen.notes;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notes")
class NoteController {

    record NewNote(String text) {}

    private final JdbcClient jdbc;

    NoteController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    List<Note> list() {
        return jdbc.sql("select id, text, created_at from note order by created_at desc, id desc")
                .query(Note.class)
                .list();
    }

    // JSON only: a cross-site form cannot send it, which is the CSRF protection (see
    // SecurityConfiguration).
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    Note create(@RequestBody NewNote note) {
        if (note.text() == null || note.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text must not be blank");
        }
        if (note.text().length() > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "text is too long");
        }
        return jdbc.sql("insert into note (text) values (:text) returning id, text, created_at")
                .param("text", note.text())
                .query(Note.class)
                .single();
    }
}
