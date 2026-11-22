package de.fourteen.notes;

import static de.fourteen.notes.CheckState.FAILURE;
import static de.fourteen.notes.CheckState.PENDING;
import static de.fourteen.notes.CheckState.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdeaStatusTest {

    @Test
    void a_running_agent_wins() {
        assertThat(IdeaStatus.of(true, null)).isEqualTo(IdeaStatus.AGENT_WORKING);
        assertThat(IdeaStatus.of(true, new Checks(SUCCESS, FAILURE)))
                .isEqualTo(IdeaStatus.AGENT_WORKING);
    }

    @Test
    void running_checks_mean_review_running() {
        assertThat(IdeaStatus.of(false, new Checks(PENDING, PENDING)))
                .isEqualTo(IdeaStatus.REVIEW_RUNNING);
        assertThat(IdeaStatus.of(false, new Checks(SUCCESS, PENDING)))
                .isEqualTo(IdeaStatus.REVIEW_RUNNING);
    }

    @Test
    void green_checks_mean_ready_for_demo() {
        assertThat(IdeaStatus.of(false, new Checks(SUCCESS, SUCCESS)))
                .isEqualTo(IdeaStatus.READY_FOR_DEMO);
    }

    @Test
    void failed_checks_or_no_pull_request_without_agent_wait_for_the_human() {
        assertThat(IdeaStatus.of(false, new Checks(SUCCESS, FAILURE)))
                .isEqualTo(IdeaStatus.WAITING_FOR_HUMAN);
        assertThat(IdeaStatus.of(false, null)).isEqualTo(IdeaStatus.WAITING_FOR_HUMAN);
    }
}
