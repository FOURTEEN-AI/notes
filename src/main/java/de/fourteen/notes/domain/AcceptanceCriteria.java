package de.fourteen.notes.domain;

import java.util.ArrayList;
import java.util.List;
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

    private static final Pattern REPLACES = Pattern.compile("@ersetzt\\(\"(.*)\"\\)");
    private static final Pattern SCENARIO =
            Pattern.compile("^(?:Szenario|Szenariogrundriss):\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    private AcceptanceCriteria() {}

    /** The content of the first ```gherkin block of a text, if it has a non-blank one. */
    public static Optional<String> gherkinBlock(String text) {
        Matcher matcher = BLOCK.matcher(text);
        return matcher.find() && !matcher.group(1).isBlank()
                ? Optional.of(matcher.group(1))
                : Optional.empty();
    }

    /**
     * The gherkin block of the newest `## Refine` comment, as long as no `## Acceptance criteria`
     * comment follows it: the coding agent proposes an amended delta (late conflict or feedback)
     * that the human has not released yet.
     */
    public static Optional<String> pendingProposal(List<Comment> comments) {
        for (int i = comments.size() - 1; i >= 0; i--) {
            String body = comments.get(i).body();
            if (body.startsWith(MARKER)) {
                return Optional.empty();
            }
            if (body.startsWith("## Refine")) {
                return gherkinBlock(body);
            }
        }
        return Optional.empty();
    }

    /**
     * The titles of the scenarios of {@code main} that a delta changes or removes: the title in
     * {@code @ersetzt("<old title>")}, and the title of a scenario tagged {@code @entfällt}.
     */
    public static List<String> oldTitles(String gherkin) {
        List<String> titles = new ArrayList<>();
        String replaced = null;
        boolean removed = false;
        for (String raw : gherkin.split("\\R")) {
            String line = raw.strip();
            Matcher replaces = REPLACES.matcher(line);
            if (replaces.find()) {
                replaced = replaces.group(1);
            }
            removed |= line.startsWith("@") && line.contains("@entfällt");
            Matcher header = SCENARIO.matcher(line);
            if (header.find()) {
                if (replaced != null) {
                    titles.add(replaced);
                } else if (removed) {
                    titles.add(header.group(1).strip());
                }
                replaced = null;
                removed = false;
            }
        }
        return titles;
    }

    public static String toComment(String gherkin) {
        return MARKER + "\n\n```gherkin\n" + gherkin + "\n```";
    }
}
