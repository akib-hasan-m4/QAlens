package com.qalens.web.testcase;

import com.qalens.domain.CasePriority;
import com.qalens.service.testcase.CaseForm;
import com.qalens.service.testcase.SuiteForm;
import com.qalens.service.testcase.SuiteService;
import com.qalens.service.testcase.SuiteView;
import com.qalens.service.testcase.TestCaseService;
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

/** Authorization is in the services; the tenant filter has already checked the organisation. */
@Controller
@RequestMapping("/o/{slug}/projects/{projectId}/suites")
public class SuiteController {

    private final SuiteService suites;
    private final TestCaseService cases;

    public SuiteController(SuiteService suites, TestCaseService cases) {
        this.suites = suites;
        this.cases = cases;
    }

    @GetMapping
    String list(@PathVariable String slug, @PathVariable Long projectId, Model model) {
        return listPage(slug, projectId, SuiteForm.blank(), model);
    }

    @PostMapping
    String create(@PathVariable String slug, @PathVariable Long projectId,
                  @Valid @ModelAttribute("form") SuiteForm form, BindingResult errors,
                  Model model, HttpServletResponse response) {
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return listPage(slug, projectId, form, model);
        }
        SuiteView created = suites.create(projectId, form);
        return "redirect:/o/" + slug + "/projects/" + projectId + "/suites/" + created.id();
    }

    @GetMapping("/{suiteId}")
    String view(@PathVariable String slug, @PathVariable Long projectId, @PathVariable Long suiteId, Model model) {
        return suitePage(slug, projectId, suiteId, CaseForm.blank(), model);
    }

    @PostMapping("/{suiteId}/rename")
    String rename(@PathVariable String slug, @PathVariable Long projectId, @PathVariable Long suiteId,
                  @Valid @ModelAttribute("renameForm") SuiteForm form, BindingResult errors,
                  Model model, HttpServletResponse response) {
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return suitePage(slug, projectId, suiteId, CaseForm.blank(), model);
        }
        suites.rename(projectId, suiteId, form);
        return "redirect:/o/" + slug + "/projects/" + projectId + "/suites/" + suiteId;
    }

    @PostMapping("/{suiteId}/cases")
    String createCase(@PathVariable String slug, @PathVariable Long projectId, @PathVariable Long suiteId,
                      @Valid @ModelAttribute("form") CaseForm form, BindingResult errors,
                      Model model, HttpServletResponse response) {
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return suitePage(slug, projectId, suiteId, form, model);
        }
        Long caseId = cases.create(projectId, suiteId, form);
        return "redirect:/o/" + slug + "/projects/" + projectId + "/cases/" + caseId;
    }

    private String listPage(String slug, Long projectId, SuiteForm form, Model model) {
        model.addAttribute("slug", slug);
        model.addAttribute("projectId", projectId);
        model.addAttribute("suites", suites.tree(projectId));
        model.addAttribute("mayEdit", suites.mayEdit(projectId));
        model.addAttribute("form", form);
        return "suites";
    }

    private String suitePage(String slug, Long projectId, Long suiteId, CaseForm form, Model model) {
        SuiteView suite = suites.view(projectId, suiteId);
        model.addAttribute("slug", slug);
        model.addAttribute("projectId", projectId);
        model.addAttribute("suite", suite);
        model.addAttribute("cases", cases.listInSuite(projectId, suiteId));
        model.addAttribute("mayEdit", suites.mayEdit(projectId));
        model.addAttribute("renameForm", new SuiteForm(suite.name(), null));
        model.addAttribute("form", form);
        model.addAttribute("priorities", CasePriority.values());
        return "suite";
    }
}
