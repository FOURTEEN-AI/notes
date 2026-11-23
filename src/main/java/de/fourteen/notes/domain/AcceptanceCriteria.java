package de.fourteen.notes.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The acceptance criteria of an idea: the Gherkin block the refine agent proposes and the human
 * approves. The approved block is posted as the comment `## Acceptance criteria`.
 */
public final class AcceptanceCriteria {

    private static final String MARKER = "## Acceptance criteria";
    private static final Pattern BLOCK =
            Pattern.compile("```gherkin\\R(.*?)\\R```", Pattern.DOTALL);

    private AcceptanceCriteria() {}

    /** The content of the first ```gherkin block of a text, if it has a non-blank one. */
    public static Optional<String> gherkinBlock(String text) {
        Matcher matcher = BLOCK.matcher(text);
        return matcher.find() && !matcher.group(1).isBlank()
                ? Optional.of(matcher.group(1))
                : Optional.empty();
    }

    public static String toComment(String gherkin) {
        return MARKER + "\n\n```gherkin\n" + gherkin + "\n```";
    }
}
