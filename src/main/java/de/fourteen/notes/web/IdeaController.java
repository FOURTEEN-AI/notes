package de.fourteen.notes.web;

import de.fourteen.notes.application.IdeaService;
import de.fourteen.notes.domain.ChatMessage;
import de.fourteen.notes.domain.Idea;
import de.fourteen.notes.domain.Scenario;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ideas")
class IdeaController {

    record NewIdea(String title, String description) {}

    record NewMessage(String text) {}

    private final IdeaService ideas;

    IdeaController(IdeaService ideas) {
        this.ideas = ideas;
    }

    @GetMapping
    List<Idea> list() {
        return ideas.list();
    }

    // JSON only, like all writes (see SecurityConfiguration).
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    Idea create(@RequestBody NewIdea idea) {
        return ideas.create(idea.title(), idea.description());
    }

    @GetMapping("/{number}/messages")
    List<ChatMessage> messages(@PathVariable int number) {
        return ideas.messages(number);
    }

    @GetMapping("/{number}/previous-scenarios")
    List<Scenario> previousScenarios(@PathVariable int number) {
        return ideas.previousScenarios(number);
    }

    @PostMapping(value = "/{number}/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ChatMessage reply(@PathVariable int number, @RequestBody NewMessage message) {
        return ideas.reply(number, message.text());
    }

    @PostMapping("/{number}/approval")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void approve(@PathVariable int number) {
        ideas.approve(number);
    }
}
