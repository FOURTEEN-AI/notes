package de.fourteen.notes.domain;

import java.util.Optional;

/**
 * A message in the chat of an idea: an issue comment that starts with one of two markers. author
 * is "me" (the human) or "agent" (the refine agent).
 */
public record ChatMessage(String author, String text) {

    // The refine agent starts on comments with the human marker (refine.yml) and writes the other.
    static final String HUMAN_MARKER = "## Idea chat";
    private static final String REFINE_MARKER = "## Refine";

    public static ChatMessage fromHuman(String text) {
        return new ChatMessage("me", text);
    }

    /** Empty for a comment that is not part of the chat. */
    public static Optional<ChatMessage> of(Comment comment) {
        String body = comment.body();
        if (body.startsWith(HUMAN_MARKER)) {
            return Optional.of(fromHuman(body.substring(HUMAN_MARKER.length()).strip()));
        }
        if (body.startsWith(REFINE_MARKER)) {
            return Optional.of(
                    new ChatMessage("agent", body.substring(REFINE_MARKER.length()).strip()));
        }
        return Optional.empty();
    }

    /** The comment body that posts a message of the human. */
    public String toComment() {
        return HUMAN_MARKER + "\n\n" + text;
    }
}
