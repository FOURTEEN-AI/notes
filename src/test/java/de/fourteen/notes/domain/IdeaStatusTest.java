package de.fourteen.notes.domain;

import static de.fourteen.notes.domain.CheckState.FAILURE;
import static de.fourteen.notes.domain.CheckState.PENDING;
import static de.fourteen.notes.domain.CheckState.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdeaStatusTest {

    @Test
    void a_running_agent_wins() {
        assertThat(IdeaStatus.of(true, null)).isEqualTo(IdeaStatus.AGENT_WORKING);
        assertThat(IdeaStatus.of(true, new Checks(SUCCESS, SUCCESS, FAILURE, SUCCESS, SUCCESS)))
                .isEqualTo(IdeaStatus.AGENT_WORKING);
    }

    @Test
    void running_checks_mean_review_running() {
        assertThat(IdeaStatus.of(false, new Checks(PENDING, PENDING, PENDING, PENDING, SUCCESS)))
                .isEqualTo(IdeaStatus.REVIEW_RUNNING);
        assertThat(IdeaStatus.of(false, new Checks(SUCCESS, PENDING, SUCCESS, SUCCESS, SUCCESS)))
                .isEqualTo(IdeaStatus.REVIEW_RUNNING);
        assertThat(IdeaStatus.of(false, new Checks(SUCCESS, SUCCESS, PENDING, SUCCESS, SUCCESS)))
                .isEqualTo(IdeaStatus.REVIEW_RUNNING);
        assertThat(IdeaStatus.of(false, new Checks(SUCCESS, SUCCESS, SUCCESS, PENDING, SUCCESS)))
                .isEqualTo(IdeaStatus.REVIEW_RUNNING);
    }

    @Test
    void green_checks_mean_ready_for_demo() {
        assertThat(IdeaStatus.of(false, new Checks(SUCCESS, SUCCESS, SUCCESS, SUCCESS, SUCCESS)))
                .isEqualTo(IdeaStatus.READY_FOR_DEMO);
    }

    @Test
    void failed_checks_or_no_pull_request_without_agent_wait_for_the_human() {
        assertThat(IdeaStatus.of(false, new Checks(SUCCESS, SUCCESS, FAILURE, SUCCESS, SUCCESS)))
                .isEqualTo(IdeaStatus.WAITING_FOR_HUMAN);
        assertThat(IdeaStatus.of(false, new Checks(SUCCESS, SUCCESS, SUCCESS, FAILURE, SUCCESS)))
                .isEqualTo(IdeaStatus.WAITING_FOR_HUMAN);
        assertThat(IdeaStatus.of(false, new Checks(SUCCESS, FAILURE, SUCCESS, SUCCESS, SUCCESS)))
                .isEqualTo(IdeaStatus.WAITING_FOR_HUMAN);
        assertThat(IdeaStatus.of(false, null)).isEqualTo(IdeaStatus.WAITING_FOR_HUMAN);
    }
}
