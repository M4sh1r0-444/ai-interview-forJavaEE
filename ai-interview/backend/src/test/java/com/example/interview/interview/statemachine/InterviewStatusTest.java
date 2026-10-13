package com.example.interview.interview.statemachine;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 计划 §8.1 InterviewStatusTest：合法转换全部通过；3 个以上非法转换抛异常。
 */
class InterviewStatusTest {

    @Test
    void legalTransitionsPass() {
        assertThat(InterviewStatus.CREATED.canTransitionTo(InterviewStatus.RUNNING)).isTrue();
        assertThat(InterviewStatus.CREATED.canTransitionTo(InterviewStatus.ABORTED)).isTrue();
        assertThat(InterviewStatus.RUNNING.canTransitionTo(InterviewStatus.WAITING_USER)).isTrue();
        assertThat(InterviewStatus.WAITING_USER.canTransitionTo(InterviewStatus.PROCESSING_AI)).isTrue();
        assertThat(InterviewStatus.WAITING_USER.canTransitionTo(InterviewStatus.FINISHED)).isTrue();
        assertThat(InterviewStatus.WAITING_USER.canTransitionTo(InterviewStatus.TIMEOUT)).isTrue();
        assertThat(InterviewStatus.PROCESSING_AI.canTransitionTo(InterviewStatus.WAITING_USER)).isTrue();
        assertThat(InterviewStatus.PROCESSING_AI.canTransitionTo(InterviewStatus.FINISHED)).isTrue();
        assertThat(InterviewStatus.PROCESSING_AI.canTransitionTo(InterviewStatus.TIMEOUT)).isTrue();
        assertThat(InterviewStatus.RUNNING.canTransitionTo(InterviewStatus.ABORTED)).isTrue();
        assertThat(InterviewStatus.WAITING_USER.canTransitionTo(InterviewStatus.ABORTED)).isTrue();
        assertThat(InterviewStatus.PROCESSING_AI.canTransitionTo(InterviewStatus.ABORTED)).isTrue();
    }

    @Test
    void illegalTransitionsThrow() {
        assertThatThrownBy(() -> InterviewStatus.CREATED.assertCanTransitionTo(InterviewStatus.FINISHED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> InterviewStatus.FINISHED.assertCanTransitionTo(InterviewStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> InterviewStatus.WAITING_USER.assertCanTransitionTo(InterviewStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> InterviewStatus.RUNNING.assertCanTransitionTo(InterviewStatus.TIMEOUT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> InterviewStatus.ABORTED.assertCanTransitionTo(InterviewStatus.WAITING_USER))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> InterviewStatus.TIMEOUT.assertCanTransitionTo(InterviewStatus.CREATED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void terminalStatesHaveNoTargetsAndCorrectSources() {
        for (InterviewStatus terminal :
                Set.of(InterviewStatus.FINISHED, InterviewStatus.ABORTED, InterviewStatus.TIMEOUT)) {
            assertThat(terminal.isTerminal()).isTrue();
            assertThat(InterviewStatus.sourcesOf(terminal)).doesNotContain(terminal);
        }
        assertThat(InterviewStatus.CREATED.isTerminal()).isFalse();
        // abort 的合法来源 = 全部非终态（计划 §5.3 #14"非 FINISHED 均可"）
        assertThat(InterviewStatus.sourcesOf(InterviewStatus.ABORTED)).containsExactlyInAnyOrder(
                InterviewStatus.CREATED, InterviewStatus.RUNNING,
                InterviewStatus.WAITING_USER, InterviewStatus.PROCESSING_AI);
        // TIMEOUT 仅 WAITING_USER/PROCESSING_AI（计划 §6.1）
        assertThat(InterviewStatus.sourcesOf(InterviewStatus.TIMEOUT)).containsExactlyInAnyOrder(
                InterviewStatus.WAITING_USER, InterviewStatus.PROCESSING_AI);
    }
}
