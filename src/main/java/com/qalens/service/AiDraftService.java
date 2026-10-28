package com.qalens.service;

import com.qalens.ai.CaseDraft;
import com.qalens.ai.CaseGenerator;
import com.qalens.ai.GenerationResult;
import com.qalens.common.ResourceNotFoundException;
import com.qalens.domain.Requirement;
import com.qalens.repository.RequirementRepository;
import com.qalens.repository.TestSuiteRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Generates test-case drafts for a requirement and saves the ones the user accepts. */
@Service
public class AiDraftService {

    private final CaseGenerator generator;
    private final RequirementRepository requirements;
    private final TestSuiteRepository suites;
    private final TestCaseService testCases;
    private final TraceLinkService links;

    public AiDraftService(CaseGenerator generator, RequirementRepository requirements, TestSuiteRepository suites,
                          TestCaseService testCases, TraceLinkService links) {
        this.generator = generator;
        this.requirements = requirements;
        this.suites = suites;
        this.testCases = testCases;
        this.links = links;
    }

    /** Not transactional: the model call can take a minute and must not hold a database connection. */
    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER')")
    public GeneratedDrafts generate(Long projectId, Long requirementId) {
        Requirement r = requirements.findByIdAndProjectId(requirementId, projectId)
                .orElseThrow(ResourceNotFoundException::new);
        GenerationResult result = generator.generate(projectId, r.getExtKey(), r.getTitle(), r.getBody());
        DraftsForm form = new DraftsForm();
        if (result.status() != GenerationResult.Status.OK) {
            return new GeneratedDrafts(false, result.message(), 0, form);
        }
        form.setDrafts(result.drafts().stream().map(AiDraftService::toInput).toList());
        return new GeneratedDrafts(true, null, result.dropped(), form);
    }

    /**
     * Saves each selected draft as a new AI_GENERATED case in the suite and links it to the requirement, all in one
     * transaction. The caller has already validated the selected drafts. Returns the number of cases created.
     */
    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD', 'TESTER')")
    @Transactional
    public int accept(Long projectId, Long requirementId, Long suiteId, List<? extends CaseForm> drafts) {
        requirements.findByIdAndProjectId(requirementId, projectId).orElseThrow(ResourceNotFoundException::new);
        suites.findByIdAndProjectId(suiteId, projectId).orElseThrow(ResourceNotFoundException::new);
        if (drafts.isEmpty() || drafts.size() > DraftsForm.MAX_DRAFTS) {
            throw new IllegalArgumentException("Between 1 and " + DraftsForm.MAX_DRAFTS + " drafts can be accepted");
        }
        for (CaseForm draft : drafts) {
            Long caseId = testCases.createGenerated(projectId, suiteId, draft);
            links.link(projectId, caseId, requirementId);
        }
        return drafts.size();
    }

    private static DraftInput toInput(CaseDraft draft) {
        DraftInput input = new DraftInput();
        input.setTitle(draft.title());
        input.setPrecondition(draft.precondition());
        input.setPriority(draft.priority());
        input.setSteps(draft.steps().stream().map(s -> new StepInput(s.action(), s.expected()))
                .collect(Collectors.toCollection(ArrayList::new)));
        input.setSelected(true);
        return input;
    }
}
