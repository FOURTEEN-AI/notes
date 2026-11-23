package de.fourteen.notes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

class StatusPropertiesTest {

    @Property
    void a_running_agent_always_wins(
            @ForAll CheckState build,
            @ForAll CheckState review,
            @ForAll CheckState acceptance,
            @ForAll CheckState preview) {
        assertThat(IdeaStatus.of(true, new Checks(build, review, acceptance, preview)))
                .isEqualTo(IdeaStatus.AGENT_WORKING);
    }

    @Property
    void the_status_follows_the_required_checks_and_ignores_the_preview(
            @ForAll CheckState build,
            @ForAll CheckState review,
            @ForAll CheckState acceptance,
            @ForAll CheckState preview) {
        Checks checks = new Checks(build, review, acceptance, preview);
        boolean green =
                build == CheckState.SUCCESS
                        && review == CheckState.SUCCESS
                        && acceptance == CheckState.SUCCESS;
        boolean pending =
                build == CheckState.PENDING
                        || review == CheckState.PENDING
                        || acceptance == CheckState.PENDING;

        assertThat(checks.green()).isEqualTo(green);
        assertThat(checks.pending()).isEqualTo(pending);
        assertThat(IdeaStatus.of(false, checks))
                .isEqualTo(
                        green
                                ? IdeaStatus.READY_FOR_DEMO
                                : pending
                                        ? IdeaStatus.REVIEW_RUNNING
                                        : IdeaStatus.WAITING_FOR_HUMAN);
    }

    @Property
    void green_and_pending_exclude_each_other(
            @ForAll CheckState build,
            @ForAll CheckState review,
            @ForAll CheckState acceptance,
            @ForAll CheckState preview) {
        Checks checks = new Checks(build, review, acceptance, preview);

        assertThat(checks.green() && checks.pending()).isFalse();
    }

    @Property
    void refine_status_follows_the_running_refine_agent(@ForAll boolean refineRuns) {
        assertThat(IdeaStatus.refine(refineRuns))
                .isEqualTo(refineRuns ? IdeaStatus.REFINING : IdeaStatus.WAITING_FOR_ANSWER);
    }

    @Property
    void a_chat_message_of_the_human_survives_its_comment(@ForAll String text) {
        ChatMessage message = ChatMessage.fromHuman(text.strip());

        assertThat(ChatMessage.of(new Comment(message.toComment()))).contains(message);
    }

    @Property
    void a_comment_without_marker_is_no_chat_message(@ForAll String body) {
        assertThat(ChatMessage.of(new Comment(" " + body))).isEmpty();
    }

    @Property
    void only_agent_branches_name_an_issue(@ForAll int number, @ForAll String other) {
        PullRequest agent = pr("agent/issue-" + Math.abs(number % 100000));

        assertThat(agent.issue()).hasValue(Math.abs(number % 100000));
        assertThat(pr("x" + other).issue()).isEmpty();
    }

    private static PullRequest pr(String branch) {
        return new PullRequest(1, "t", "b", "u", branch, "sha", "open");
    }
}
