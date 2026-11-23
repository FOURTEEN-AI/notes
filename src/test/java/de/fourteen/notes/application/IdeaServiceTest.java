package de.fourteen.notes.application;

import static de.fourteen.notes.domain.CheckState.PENDING;
import static de.fourteen.notes.domain.CheckState.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fourteen.notes.domain.ChatMessage;
import de.fourteen.notes.domain.Checks;
import de.fourteen.notes.domain.Comment;
import de.fourteen.notes.domain.DomainException;
import de.fourteen.notes.domain.GitHub;
import de.fourteen.notes.domain.Idea;
import de.fourteen.notes.domain.IdeaStatus;
import de.fourteen.notes.domain.Issue;
import de.fourteen.notes.domain.PullRequest;
import de.fourteen.notes.domain.Scenario;
import de.fourteen.notes.domain.ScenarioReport;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdeaServiceTest {

    private final GitHub github = mock(GitHub.class);
    private final ScenarioReport report = mock(ScenarioReport.class);
    private final IdeaService service = new IdeaService(github, report);

    private static PullRequest pr(int number, String branch) {
        return new PullRequest(number, "t", "b", "u", branch, "sha" + number, "open");
    }

    @Test
    void a_refining_idea_waits_for_an_answer_or_is_refined() {
        when(github.openIdeaIssues())
                .thenReturn(List.of(new Issue(1, "A", "a", true), new Issue(2, "B", "b", true)));
        when(github.openPullRequests()).thenReturn(List.of());
        when(github.activeAgentRuns()).thenReturn(Set.of());
        when(github.activeRefineRuns()).thenReturn(Set.of(2));

        assertThat(service.list())
                .containsExactly(
                        new Idea(1, "A", "a", IdeaStatus.WAITING_FOR_ANSWER, null, false),
                        new Idea(2, "B", "b", IdeaStatus.REFINING, null, false));
    }

    @Test
    void a_released_idea_shows_the_status_of_its_pull_request() {
        when(github.openIdeaIssues())
                .thenReturn(
                        List.of(
                                new Issue(1, "No PR", "a", false),
                                new Issue(2, "Agent on issue", "b", false),
                                new Issue(3, "Agent on PR", "c", false),
                                new Issue(4, "Review", "d", false),
                                new Issue(5, "Ready", "e", false)));
        when(github.openPullRequests())
                .thenReturn(
                        List.of(
                                pr(12, "agent/issue-3"),
                                pr(14, "agent/issue-4"),
                                pr(15, "agent/issue-5"),
                                pr(16, "agent/issue-5")));
        when(github.activeAgentRuns()).thenReturn(Set.of(2, 12));
        when(github.activeRefineRuns()).thenReturn(Set.of());
        when(github.checks("sha14")).thenReturn(new Checks(PENDING, PENDING, PENDING, PENDING));
        when(github.checks("sha15")).thenReturn(new Checks(SUCCESS, SUCCESS, SUCCESS, SUCCESS));
        when(github.checks("sha12")).thenReturn(new Checks(SUCCESS, SUCCESS, SUCCESS, PENDING));

        List<Idea> ideas = service.list();

        assertThat(ideas)
                .containsExactly(
                        new Idea(1, "No PR", "a", IdeaStatus.WAITING_FOR_HUMAN, null, false),
                        new Idea(2, "Agent on issue", "b", IdeaStatus.AGENT_WORKING, null, false),
                        new Idea(
                                3,
                                "Agent on PR",
                                "c",
                                IdeaStatus.AGENT_WORKING,
                                new Issue(3, "", "", false).previewUrl(),
                                false),
                        new Idea(
                                4,
                                "Review",
                                "d",
                                IdeaStatus.REVIEW_RUNNING,
                                new Issue(4, "", "", false).previewUrl(),
                                false),
                        new Idea(
                                5,
                                "Ready",
                                "e",
                                IdeaStatus.READY_FOR_DEMO,
                                new Issue(5, "", "", false).previewUrl(),
                                true));
        verify(github, never()).checks("sha16");
    }

    @Test
    void creates_an_idea_with_a_stripped_title_and_an_empty_description_by_default() {
        when(github.createIssue("Titel", "")).thenReturn(new Issue(7, "Titel", "", true));
        when(github.createIssue("Titel", "Text")).thenReturn(new Issue(8, "Titel", "Text", true));

        assertThat(service.create(" Titel ", null))
                .isEqualTo(new Idea(7, "Titel", "", IdeaStatus.REFINING, null, false));
        assertThat(service.create("Titel", "Text").description()).isEqualTo("Text");
    }

    @Test
    void refuses_an_idea_without_title() {
        for (String title : new String[] {null, "", " "}) {
            assertThatThrownBy(() -> service.create(title, "x"))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            e -> assertThat(e.kind()).isEqualTo(DomainException.Kind.INVALID));
        }
        verify(github, never()).createIssue(anyString(), anyString());
    }

    @Test
    void the_chat_consists_of_the_marked_comments() {
        when(github.comments(1))
                .thenReturn(
                        List.of(
                                new Comment("## Idea chat\n\nFrage"),
                                new Comment("Preview: ..."),
                                new Comment("## Refine\n\nAntwort")));

        assertThat(service.messages(1))
                .containsExactly(
                        new ChatMessage("me", "Frage"), new ChatMessage("agent", "Antwort"));
    }

    @Test
    void a_reply_is_posted_stripped_with_the_chat_marker() {
        ChatMessage message = service.reply(3, "  Lieber CSV \n");

        assertThat(message).isEqualTo(new ChatMessage("me", "Lieber CSV"));
        verify(github).comment(3, "## Idea chat\n\nLieber CSV");
    }

    @Test
    void refuses_a_blank_reply() {
        for (String text : new String[] {null, "", " \n"}) {
            assertThatThrownBy(() -> service.reply(3, text))
                    .isInstanceOfSatisfying(
                            DomainException.class,
                            e -> assertThat(e.kind()).isEqualTo(DomainException.Kind.INVALID));
        }
        verify(github, never()).comment(any(Integer.class), anyString());
    }

    @Test
    void the_previous_scenarios_are_those_the_newest_proposal_replaces() {
        when(github.comments(1))
                .thenReturn(
                        List.of(
                                new Comment(
                                        "## Refine\n\n```gherkin\n@ersetzt(\"Alt\")\nSzenario: Neu\n```"),
                                new Comment("## Idea chat\n\nnoch was"),
                                new Comment(
                                        "## Refine\n\n```gherkin\n@ersetzt(\"Älter\")\nSzenario: Neuer\n```"),
                                new Comment("Preview: ...")));
        Scenario older = new Scenario("Älter", List.of("Wenn x"));
        when(report.find("Älter")).thenReturn(Optional.of(older));
        when(report.find("Alt")).thenReturn(Optional.of(new Scenario("Alt", List.of())));

        assertThat(service.previousScenarios(1)).containsExactly(older);
    }

    @Test
    void there_are_no_previous_scenarios_without_a_refine_proposal() {
        when(github.comments(1))
                .thenReturn(List.of(new Comment("```gherkin\n@entfällt\nSzenario: X\n```")));

        assertThat(service.previousScenarios(1)).isEmpty();
    }

    @Test
    void approving_posts_the_criteria_and_then_releases() {
        when(github.comments(5))
                .thenReturn(List.of(new Comment("## Refine\n\n```gherkin\nSzenario: Neu\n```")));

        service.approve(5);

        var order = inOrder(github);
        order.verify(github).comment(5, "## Acceptance criteria\n\n```gherkin\nSzenario: Neu\n```");
        order.verify(github).release(5);
    }

    @Test
    void approving_without_a_pending_proposal_is_a_conflict() {
        when(github.comments(5)).thenReturn(List.of(new Comment("## Idea chat\n\nhm")));

        assertThatThrownBy(() -> service.approve(5))
                .isInstanceOfSatisfying(
                        DomainException.class,
                        e -> assertThat(e.kind()).isEqualTo(DomainException.Kind.CONFLICT));
        verify(github, never()).release(5);
    }
}
