package com.qalens.web;

import com.qalens.domain.RequirementPriority;
import com.qalens.domain.RequirementStatus;
import com.qalens.service.RequirementForm;
import com.qalens.service.RequirementService;
import com.qalens.service.RequirementView;
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

/** Authorization is in {@link RequirementService}; the tenant filter has already checked the organisation. */
@Controller
@RequestMapping("/o/{slug}/projects/{projectId}/requirements")
public class RequirementController {

    private final RequirementService requirements;

    public RequirementController(RequirementService requirements) {
        this.requirements = requirements;
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
        model.addAttribute("mayEdit", requirements.mayEdit(projectId));
        model.addAttribute("form", form);
        addChoices(model);
        return "requirement";
    }

    private static void addChoices(Model model) {
        model.addAttribute("statuses", RequirementStatus.values());
        model.addAttribute("priorities", RequirementPriority.values());
    }
}
