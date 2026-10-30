package com.qalens.web.requirement;

import com.qalens.domain.CasePriority;
import com.qalens.domain.RequirementPriority;
import com.qalens.domain.RequirementStatus;
import com.qalens.service.ai.AiDraftService;
import com.qalens.service.ai.DraftInput;
import com.qalens.service.ai.DraftsForm;
import com.qalens.service.ai.GeneratedDrafts;
import com.qalens.service.ai.ImpactService;
import com.qalens.service.ai.ImpactSuggestions;
import com.qalens.service.requirement.RequirementForm;
import com.qalens.service.requirement.RequirementService;
import com.qalens.service.requirement.RequirementView;
import com.qalens.service.testcase.SuiteService;
import com.qalens.service.requirement.TraceLinkService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Authorization is in {@link RequirementService}; the tenant filter has already checked the organisation. */
@Controller
@RequestMapping("/o/{slug}/projects/{projectId}/requirements")
public class RequirementController {

    private final RequirementService requirements;
    private final TraceLinkService links;
    private final AiDraftService aiDrafts;
    private final SuiteService suites;
    private final ImpactService impact;
    private final SpringValidatorAdapter validator;

    public RequirementController(RequirementService requirements, TraceLinkService links, AiDraftService aiDrafts,
                                 SuiteService suites, ImpactService impact, Validator validator) {
        this.requirements = requirements;
        this.links = links;
        this.aiDrafts = aiDrafts;
        this.suites = suites;
        this.impact = impact;
        this.validator = new SpringValidatorAdapter(validator);
    }

    @GetMapping
    String list(@PathVariable String slug, @PathVariable Long projectId,
                @RequestParam(required = false) String q, @RequestParam(required = false) String sort,
                Model model) {
        return listPage(slug, projectId, q, sort, RequirementForm.blank(), model);
    }

    @PostMapping
    String create(@PathVariable String slug, @PathVariable Long projectId,
                  @Valid @ModelAttribute("form") RequirementForm form, BindingResult errors,
                  Model model, HttpServletResponse response) {
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return listPage(slug, projectId, null, null, form, model);
        }
        RequirementView created = requirements.create(projectId, form);
        return "redirect:/o/" + slug + "/projects/" + projectId + "/requirements/" + created.id();
    }

    @GetMapping("/{id}")
    String view(@PathVariable String slug, @PathVariable Long projectId, @PathVariable Long id, Model model) {
        RequirementView requirement = requirements.view(projectId, id);
        return detailPage(slug, projectId, requirement, new RequirementForm(requirement.extKey(),
                requirement.title(), requirement.body(), requirement.status(), requirement.priority()), model);
    }

    @PostMapping("/{id}")
    String update(@PathVariable String slug, @PathVariable Long projectId, @PathVariable Long id,
                  @Valid @ModelAttribute("form") RequirementForm form, BindingResult errors,
                  Model model, HttpServletResponse response) {
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return detailPage(slug, projectId, requirements.view(projectId, id), form, model);
        }
        requirements.update(projectId, id, form);
        return "redirect:/o/" + slug + "/projects/" + projectId + "/requirements/" + id;
    }

    /** Asks the model for drafts. An HTMX request gets just the review panel; a plain post gets the whole page. */
    @PostMapping("/{id}/drafts")
    String generate(@PathVariable String slug, @PathVariable Long projectId, @PathVariable Long id,
                    @RequestHeader(value = "HX-Request", required = false) String htmx, Model model) {
        GeneratedDrafts generated = aiDrafts.generate(projectId, id);
        addPanel(projectId, generated.ok(), generated.message(), generated.dropped(), generated.form(), model);
        if (htmx != null) {
            model.addAttribute("slug", slug);
            model.addAttribute("projectId", projectId);
            model.addAttribute("requirementId", id);
            return "requirement :: ai-panel";
        }
        return view(slug, projectId, id, model);
    }

    /** Saves the ticked drafts. Each one is validated like a manually written case before anything is saved. */
    @PostMapping("/{id}/drafts/accept")
    String accept(@PathVariable String slug, @PathVariable Long projectId, @PathVariable Long id,
                  @ModelAttribute("draftsForm") DraftsForm form, BindingResult errors, Model model,
                  HttpServletResponse response, RedirectAttributes redirect) {
        List<DraftInput> all = form.getDrafts();
        List<DraftInput> selected = form.selectedDrafts();
        if (selected.isEmpty()) {
            errors.reject("drafts.none", "Tick at least one draft to accept");
        }
        if (all.size() > DraftsForm.MAX_DRAFTS) {
            errors.reject("drafts.many", "Too many drafts in one request");
        }
        if (form.getSuiteId() == null) {
            errors.rejectValue("suiteId", "suite.required", "Choose the suite the new test cases go into");
        }
        for (int i = 0; i < all.size() && all.size() <= DraftsForm.MAX_DRAFTS; i++) {
            if (all.get(i) != null && all.get(i).isSelected()) {
                errors.pushNestedPath("drafts[" + i + "]");
                validator.validate(all.get(i), errors);
                errors.popNestedPath();
            }
        }
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            addPanel(projectId, true, null, 0, form, model);
            return view(slug, projectId, id, model);
        }
        int created = aiDrafts.accept(projectId, id, form.getSuiteId(), selected);
        redirect.addFlashAttribute("notice", created == 1 ? "1 AI-generated test case added and linked."
                : created + " AI-generated test cases added and linked.");
        return "redirect:/o/" + slug + "/projects/" + projectId + "/requirements/" + id;
    }

    /** Lists existing cases this requirement probably affects. HTMX gets just the panel; a plain post the page. */
    @PostMapping("/{id}/impact")
    String impact(@PathVariable String slug, @PathVariable Long projectId, @PathVariable Long id,
                  @RequestHeader(value = "HX-Request", required = false) String htmx, Model model) {
        ImpactSuggestions suggestions = impact.suggest(projectId, id);
        model.addAttribute("impactPanel", true);
        model.addAttribute("impact", suggestions);
        if (htmx != null) {
            model.addAttribute("slug", slug);
            model.addAttribute("projectId", projectId);
            model.addAttribute("requirementId", id);
            model.addAttribute("mayEdit", requirements.mayEdit(projectId));
            return "requirement :: impact-panel";
        }
        return view(slug, projectId, id, model);
    }

    /** Links a suggested case to this requirement and comes back to the requirement page. */
    @PostMapping("/{id}/links")
    String linkCase(@PathVariable String slug, @PathVariable Long projectId, @PathVariable Long id,
                    @RequestParam Long caseId, RedirectAttributes redirect) {
        links.link(projectId, caseId, id);
        redirect.addFlashAttribute("notice", "Test case linked to this requirement.");
        return "redirect:/o/" + slug + "/projects/" + projectId + "/requirements/" + id;
    }

    private void addPanel(Long projectId, boolean ok, String message, int dropped, DraftsForm form, Model model) {
        model.addAttribute("aiPanel", true);
        model.addAttribute("aiOk", ok);
        model.addAttribute("aiMessage", message);
        model.addAttribute("aiDropped", dropped);
        model.addAttribute("draftsForm", form);
        model.addAttribute("suites", suites.tree(projectId));
        model.addAttribute("casePriorities", CasePriority.values());
    }

    private String listPage(String slug, Long projectId, String q, String sort, RequirementForm form, Model model) {
        model.addAttribute("slug", slug);
        model.addAttribute("projectId", projectId);
        model.addAttribute("requirements", requirements.list(projectId, q, sort));
        model.addAttribute("mayEdit", requirements.mayEdit(projectId));
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("sort", sort == null ? "" : sort);
        model.addAttribute("form", form);
        addChoices(model);
        return "requirements";
    }

    private String detailPage(String slug, Long projectId, RequirementView requirement, RequirementForm form,
                              Model model) {
        model.addAttribute("slug", slug);
        model.addAttribute("projectId", projectId);
        model.addAttribute("requirement", requirement);
        model.addAttribute("requirementId", requirement.id());
        model.addAttribute("linkedCases", links.casesOfRequirement(projectId, requirement.id()));
        model.addAttribute("mayEdit", requirements.mayEdit(projectId));
        model.addAttribute("mayImpact", impact.mayUse(projectId));
        model.addAttribute("form", form);
        addChoices(model);
        return "requirement";
    }

    private static void addChoices(Model model) {
        model.addAttribute("statuses", RequirementStatus.values());
        model.addAttribute("priorities", RequirementPriority.values());
    }
}
