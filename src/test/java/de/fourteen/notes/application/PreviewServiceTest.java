package de.fourteen.notes.application;

import static de.fourteen.notes.domain.CheckState.FAILURE;
import static de.fourteen.notes.domain.CheckState.PENDING;
import static de.fourteen.notes.domain.CheckState.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.fourteen.notes.application.PreviewService.Preview;
import de.fourteen.notes.domain.Checks;
import de.fourteen.notes.domain.Comment;
import de.fourteen.notes.domain.DemoStep;
import de.fourteen.notes.domain.DomainException;
import de.fourteen.notes.domain.GitHub;
import de.fourteen.notes.domain.PullRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PreviewServiceTest {

    private static final Checks GREEN = new Checks(SUCCESS, SUCCESS, SUCCESS, PENDING);

    private final GitHub github = mock(GitHub.class);
    private final PreviewService service =
            new PreviewService(github, JsonMapper.builder().build(), 9, "deployed");

    private PullRequest pr(String branch, String state, String body) {
        PullRequest pr = new PullRequest(9, "Titel", body, "u", branch, "sha", state);
        when(github.pullRequest(9)).thenReturn(pr);
        return pr;
    }

    private void kind(Runnable action, DomainException.Kind kind) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        DomainException.class, e -> assertThat(e.kind()).isEqualTo(kind));
    }

    @Test
    void is_a_preview_only_with_a_pull_request_number() {
        assertThat(service.isPreview()).isTrue();
        PreviewService none = new PreviewService(github, JsonMapper.builder().build(), null, null);
        assertThat(none.isPreview()).isFalse();
        kind(none::get, DomainException.Kind.NOT_FOUND);
        kind(() -> none.feedback("x"), DomainException.Kind.NOT_FOUND);
        kind(none::merge, DomainException.Kind.NOT_FOUND);
    }

    @Test
    void shows_the_open_pull_request_of_an_idea_with_its_proposal() {
        pr(
                "agent/issue-4",
                "open",
                "```demo\n[{\"do\":\"A\",\"expect\":\"B\",\"selector\":\"main\"}]\n```");
        when(github.checks("sha")).thenReturn(new Checks(SUCCESS, FAILURE, PENDING, SUCCESS));
        when(github.comments(4))
                .thenReturn(List.of(new Comment("## Refine\n\n```gherkin\nSzenario: N\n```")));

        assertThat(service.get())
                .isEqualTo(
                        new Preview(
                                9,
                                "Titel",
                                "open",
                                "deployed",
                                SUCCESS,
                                FAILURE,
                                PENDING,
                                false,
                                List.of(new DemoStep("A", "B", "main")),
                                4,
                                "Szenario: N"));
    }

    @Test
    void an_open_pull_request_with_green_checks_is_mergeable() {
        pr("feature", "open", "");
        when(github.checks("sha")).thenReturn(GREEN);

        Preview preview = service.get();

        assertThat(preview.mergeable()).isTrue();
        assertThat(preview.issue()).isNull();
        assertThat(preview.proposal()).isNull();
        assertThat(preview.demo()).isEmpty();
        verify(github, never()).comments(anyInt());
    }

    @Test
    void a_merged_pull_request_is_not_mergeable_and_has_no_issue() {
        pr("agent/issue-4", "merged", "");
        when(github.checks("sha")).thenReturn(GREEN);

        Preview preview = service.get();

        assertThat(preview.mergeable()).isFalse();
        assertThat(preview.issue()).isNull();
        assertThat(preview.proposal()).isNull();
    }

    @Test
    void an_issue_without_proposal_has_a_null_proposal() {
        pr("agent/issue-4", "open", "");
        when(github.checks("sha")).thenReturn(GREEN);
        when(github.comments(4)).thenReturn(List.of());

        Preview preview = service.get();

        assertThat(preview.issue()).isEqualTo(4);
        assertThat(preview.proposal()).isNull();
    }

    @Test
    void the_demo_block_must_be_valid_json_and_steps_need_an_action() {
        pr(
                "x",
                "open",
                "text\n```demo\n[{\"do\":\"A\",\"expect\":\"B\"},{\"do\":\" \",\"expect\":\"C\"},{\"expect\":\"D\"},null]\n```\nmore");
        when(github.checks("sha")).thenReturn(GREEN);
        assertThat(service.get().demo()).containsExactly(new DemoStep("A", "B", null));

        pr("x", "open", "```demo\nnot json\n```");
        assertThat(service.get().demo()).isEmpty();
        pr("x", "open", "```demo\n[]\n```");
        assertThat(service.get().demo()).isEmpty();
        pr("x", "open", "```gherkin\n[{\"do\":\"A\",\"expect\":\"B\"}]\n```");
        assertThat(service.get().demo()).isEmpty();
    }

    @Test
    void feedback_is_posted_stripped_under_the_marker() {
        service.feedback("  Button fehlt \n");

        verify(github).comment(9, "## Human feedback\n\nButton fehlt");
    }

    @Test
    void refuses_blank_feedback() {
        kind(() -> service.feedback(null), DomainException.Kind.INVALID);
        kind(() -> service.feedback(" "), DomainException.Kind.INVALID);
        verify(github, never()).comment(anyInt(), anyString());
    }

    @Test
    void merges_the_checked_commit_of_an_open_pull_request_with_green_checks() {
        pr("x", "open", "");
        when(github.checks("sha")).thenReturn(GREEN);

        service.merge();

        verify(github).merge(9, "sha");
    }

    @Test
    void refuses_to_merge_unless_open_and_green() {
        pr("x", "open", "");
        when(github.checks("sha")).thenReturn(new Checks(SUCCESS, SUCCESS, PENDING, SUCCESS));
        kind(service::merge, DomainException.Kind.CONFLICT);

        pr("x", "closed", "");
        when(github.checks("sha")).thenReturn(GREEN);
        kind(service::merge, DomainException.Kind.CONFLICT);

        verify(github, never()).merge(anyInt(), anyString());
    }
}
