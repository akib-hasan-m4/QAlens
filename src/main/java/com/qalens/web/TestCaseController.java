package com.qalens.web;

import com.qalens.domain.CasePriority;
import com.qalens.service.CaseForm;
import com.qalens.service.CaseVersionView;
import com.qalens.service.CaseView;
import com.qalens.service.TestCaseService;
import com.qalens.service.TraceLinkService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Authorization is in {@link TestCaseService}; the tenant filter has already checked the organisation. */
@Controller
@RequestMapping("/o/{slug}/projects/{projectId}/cases/{caseId}")
public class TestCaseController {

    private final TestCaseService cases;
    private final TraceLinkService links;

    public TestCaseController(TestCaseService cases, TraceLinkService links) {
        this.cases = cases;
        this.links = links;
    }

    /** {@code version} selects a past version to read; the edit form is prefilled from what is shown. */
    @GetMapping
    String view(@PathVariable String slug, @PathVariable Long projectId, @PathVariable Long caseId,
                @RequestParam(required = false) Integer version, Model model) {
        CaseView view = cases.view(projectId, caseId, version);
        CaseVersionView shown = view.shown();
        return page(slug, projectId, view,
                CaseForm.of(shown.title(), shown.precondition(), shown.priority(), shown.steps()), model);
    }

    /** Saving never edits a row: it appends the next version. */
    @PostMapping
    String save(@PathVariable String slug, @PathVariable Long projectId, @PathVariable Long caseId,
                @Valid @ModelAttribute("form") CaseForm form, BindingResult errors,
                Model model, HttpServletResponse response) {
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return page(slug, projectId, cases.view(projectId, caseId, null), form, model);
        }
        cases.saveNewVersion(projectId, caseId, form);
        return "redirect:/o/" + slug + "/projects/" + projectId + "/cases/" + caseId;
    }

    @PostMapping("/requirements")
    String link(@PathVariable String slug, @PathVariable Long projectId, @PathVariable Long caseId,
                @RequestParam Long requirementId) {
        links.link(projectId, caseId, requirementId);
        return "redirect:/o/" + slug + "/projects/" + projectId + "/cases/" + caseId;
    }

    @PostMapping("/requirements/{requirementId}/remove")
    String unlink(@PathVariable String slug, @PathVariable Long projectId, @PathVariable Long caseId,
                  @PathVariable Long requirementId) {
        links.unlink(projectId, caseId, requirementId);
        return "redirect:/o/" + slug + "/projects/" + projectId + "/cases/" + caseId;
    }

    private String page(String slug, Long projectId, CaseView view, CaseForm form, Model model) {
        model.addAttribute("slug", slug);
        model.addAttribute("projectId", projectId);
        model.addAttribute("tc", view);
        model.addAttribute("mayEdit", cases.mayEdit(projectId));
        model.addAttribute("form", form);
        model.addAttribute("priorities", CasePriority.values());
        model.addAttribute("linked", links.requirementsOfCase(projectId, view.id()));
        model.addAttribute("linkable", links.linkableRequirements(projectId, view.id()));
        return "test-case";
    }
}
