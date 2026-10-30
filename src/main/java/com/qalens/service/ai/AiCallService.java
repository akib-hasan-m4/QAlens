package com.qalens.service.ai;

import com.qalens.domain.AiCallLog;
import com.qalens.repository.AiCallLogRepository;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only history of the model calls made for a project. */
@Service
public class AiCallService {

    /** Matches the {@code output_ref} written by the case generator, e.g. {@code drafts=4,dropped=1}. */
    private static final Pattern DRAFT_COUNTS = Pattern.compile("drafts=(\\d+),dropped=(\\d+)");

    /** Matches the {@code output_ref} written by the impact matcher, e.g. {@code candidates=5,embedded=2}. */
    private static final Pattern CANDIDATE_COUNT = Pattern.compile("candidates=(\\d+),embedded=\\d+");

    private final AiCallLogRepository calls;

    public AiCallService(AiCallLogRepository calls) {
        this.calls = calls;
    }

    /** The 20 most recent calls, newest first. */
    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD')")
    @Transactional(readOnly = true)
    public List<AiCallView> recent(Long projectId) {
        return calls.findTop20ByProjectIdOrderByCreatedAtDescIdDesc(projectId).stream()
                .map(AiCallService::toView).toList();
    }

    static AiCallView toView(AiCallLog log) {
        Integer drafts = null;
        Integer dropped = null;
        Integer candidates = null;
        if (log.getOutputRef() != null) {
            Matcher m = DRAFT_COUNTS.matcher(log.getOutputRef());
            if (m.matches()) {
                drafts = Integer.valueOf(m.group(1));
                dropped = Integer.valueOf(m.group(2));
            }
            Matcher c = CANDIDATE_COUNT.matcher(log.getOutputRef());
            if (c.matches()) {
                candidates = Integer.valueOf(c.group(1));
            }
        }
        return new AiCallView(log.getCreatedAt(), log.getFeature(), log.getModel(), log.getPromptVersion(),
                log.getStatus(), log.getTokensIn(), log.getTokensOut(), log.getLatencyMs(), drafts, dropped,
                candidates, log.getErrorMessage());
    }
}
