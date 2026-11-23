package de.fourteen.notes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModelTest {

    @Test
    void an_issue_knows_its_branch_and_preview_app() {
        Issue issue = new Issue(12, "t", "b", false);

        assertThat(issue.branch()).isEqualTo("agent/issue-12");
        assertThat(issue.previewUrl()).isEqualTo("https://notes-fourteen-it-issue-12.fly.dev");
    }

    @Test
    void only_state_open_is_open() {
        assertThat(new PullRequest(1, "t", "b", "u", "x", "s", "open").open()).isTrue();
        assertThat(new PullRequest(1, "t", "b", "u", "x", "s", "closed").open()).isFalse();
        assertThat(new PullRequest(1, "t", "b", "u", "x", "s", "merged").open()).isFalse();
    }

    @Test
    void a_pull_request_without_branch_has_no_issue() {
        assertThat(new PullRequest(1, "t", "b", "u", null, "s", "open").issue()).isEmpty();
        assertThat(new PullRequest(1, "t", "b", "u", "agent/issue-x", "s", "open").issue())
                .isEmpty();
        assertThat(new PullRequest(1, "t", "b", "u", "agent/issue-7", "s", "open").issue())
                .hasValue(7);
    }

    @Test
    void the_human_chat_message_has_a_comment_and_back() {
        assertThat(ChatMessage.fromHuman("Hi").toComment()).isEqualTo("## Idea chat\n\nHi");
        assertThat(ChatMessage.of(new Comment("## Refine\n\n Q ")))
                .contains(new ChatMessage("agent", "Q"));
    }

    @Test
    void a_comment_is_the_marker_and_the_fenced_block() {
        assertThat(AcceptanceCriteria.toComment("X"))
                .isEqualTo("## Acceptance criteria\n\n```gherkin\nX\n```");
    }

    @Test
    void the_block_is_the_first_one_and_blank_blocks_are_none() {
        assertThat(AcceptanceCriteria.gherkinBlock("```gherkin\nA\n```\n```gherkin\nB\n```"))
                .contains("A");
        assertThat(AcceptanceCriteria.gherkinBlock("```gherkin\n  \n```")).isEmpty();
        assertThat(AcceptanceCriteria.gherkinBlock("```gherkin\r\nA\r\n```")).contains("A");
    }

    @Test
    void the_newest_refine_comment_decides_the_proposal() {
        Comment old = new Comment("## Refine\n\n```gherkin\nA\n```");
        Comment newer = new Comment("## Refine\n\n```gherkin\nB\n```");
        Comment question = new Comment("## Refine\n\nNo block yet");

        assertThat(AcceptanceCriteria.pendingProposal(List.of(old, newer))).contains("B");
        assertThat(AcceptanceCriteria.pendingProposal(List.of(old, question))).isEmpty();
        assertThat(AcceptanceCriteria.pendingProposal(List.of())).isEmpty();
    }

    @Test
    void a_tag_line_and_scenario_outline_title_are_found_with_any_case() {
        String gherkin =
                """
                @manuell("x") @entfällt
                szenariogrundriss:   Alt   \s
                @ersetzt("B")
                Szenario: Neu
                Szenario: Unberührt
                @entfällt
                # Kommentar
                Szenario: Weg
                Wenn ich @entfällt sage
                Szenario: Bleibt
                """;

        assertThat(AcceptanceCriteria.oldTitles(gherkin)).containsExactly("Alt", "B", "Weg");
    }
}
