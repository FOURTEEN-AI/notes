package de.fourteen.notes.application;

import de.fourteen.notes.domain.ChatMessage;
import de.fourteen.notes.domain.CheckState;
import de.fourteen.notes.domain.AcceptanceCriteria;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Feature ideas and their chats, all kept on GitHub. */
@Service
public class IdeaService {

    private final GitHub github;
    private final ScenarioReport report;

    IdeaService(GitHub github, ScenarioReport report) {
        this.github = github;
        this.report = report;
    }

    public List<Idea> list() {
        List<Issue> issues = github.openIdeaIssues();
        Map<String, PullRequest> pullRequests =
                github.openPullRequests().stream()
                        .collect(
                                Collectors.toMap(
                                        PullRequest::branch, Function.identity(), (a, b) -> a));
        Set<Integer> agentRuns = github.activeAgentRuns();
        Set<Integer> refineRuns = github.activeRefineRuns();
        return issues.stream()
                .map(
                        issue -> {
                            if (issue.refining()) {
                                return new Idea(
                                        issue.number(),
                                        issue.title(),
                                        issue.body(),
                                        IdeaStatus.refine(refineRuns.contains(issue.number())),
                                        null,
                                        false);
                            }
                            PullRequest pr = pullRequests.get(issue.branch());
                            boolean running =
                                    agentRuns.contains(issue.number())
                                            || (pr != null && agentRuns.contains(pr.number()));
                            Checks checks = pr == null ? null : github.checks(pr.sha());
                            return new Idea(
                                    issue.number(),
                                    issue.title(),
                                    issue.body(),
                                    IdeaStatus.of(running, checks),
                                    pr == null ? null : issue.previewUrl(),
                                    checks != null && checks.preview() == CheckState.SUCCESS);
                        })
                .toList();
    }

    public Idea create(String title, String description) {
        if (title == null || title.isBlank()) {
            throw new DomainException(DomainException.Kind.INVALID, "title must not be blank");
        }
        String body = description == null ? "" : description;
        Issue issue = github.createIssue(title.strip(), body);
        return new Idea(issue.number(), issue.title(), body, IdeaStatus.REFINING, null, false);
    }

    public List<ChatMessage> messages(int number) {
        return github.comments(number).stream()
                .map(ChatMessage::of)
                .flatMap(Optional::stream)
                .toList();
    }

    public ChatMessage reply(int number, String text) {
        if (text == null || text.isBlank()) {
            throw new DomainException(DomainException.Kind.INVALID, "text must not be blank");
        }
        ChatMessage message = ChatMessage.fromHuman(text.strip());
        github.comment(number, message.toComment());
        return message;
    }

    /**
     * The old versions, from the report of {@code main}, of the scenarios that the newest proposal
     * of the refine agent changes or removes.
     */
    public List<Scenario> previousScenarios(int number) {
        return github.comments(number).stream()
                .map(Comment::body)
                .filter(body -> body.startsWith("## Refine"))
                .reduce((first, second) -> second)
                .flatMap(AcceptanceCriteria::gherkinBlock)
                .stream()
                .flatMap(gherkin -> AcceptanceCriteria.oldTitles(gherkin).stream())
                .flatMap(title -> report.find(title).stream())
                .toList();
    }

    /**
     * Releases the idea with the acceptance criteria the refine agent proposed last: copies its
     * gherkin block into a comment `## Acceptance criteria`, then swaps `refine` for `agent`. The
     * scenarios stay in that comment; they never land in the repository. Refused when there is no
     * pending proposal: none yet, already released (a second click), or an answer in the chat is
     * newer than it.
     */
    public void approve(int number) {
        String proposal =
                AcceptanceCriteria.pendingProposal(github.comments(number))
                        .orElseThrow(
                                () ->
                                        new DomainException(
                                                DomainException.Kind.CONFLICT,
                                                "there is no pending proposal to approve"));
        github.comment(number, AcceptanceCriteria.toComment(proposal));
        github.release(number);
    }
}
