package de.fourteen.notes.domain;

/** Where an idea (an open issue with the label `refine` or `agent`) stands, derived from GitHub. */
public enum IdeaStatus {
    REFINING,
    WAITING_FOR_ANSWER,
    AGENT_WORKING,
    REVIEW_RUNNING,
    READY_FOR_DEMO,
    WAITING_FOR_HUMAN;

    /** An idea the refine agent has not released yet: it is thinking, or waits for the human. */
    public static IdeaStatus refine(boolean refineRuns) {
        return refineRuns ? REFINING : WAITING_FOR_ANSWER;
    }

    /**
     * @param agentRuns whether the coding agent runs or is queued for the issue or its pull request
     * @param checks the checks of the pull request, or null if there is none yet
     */
    public static IdeaStatus of(boolean agentRuns, Checks checks) {
        if (agentRuns) {
            return AGENT_WORKING;
        }
        if (checks == null) {
            // No pull request and no agent: the agent run failed.
            return WAITING_FOR_HUMAN;
        }
        if (checks.green()) {
            return READY_FOR_DEMO;
        }
        if (checks.pending()) {
            return REVIEW_RUNNING;
        }
        return WAITING_FOR_HUMAN;
    }
}
