package com.qalens.web;

import com.qalens.service.ProjectForm;
import com.qalens.service.ProjectService;
import com.qalens.service.ProjectView;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/** The organisation in the URL has already been checked by the tenant filter; authorization is in the service. */
@Controller
@RequestMapping("/o/{slug}/projects")
public class ProjectController {

    private final ProjectService projects;

    public ProjectController(ProjectService projects) {
        this.projects = projects;
    }

    @GetMapping
    String list(@PathVariable String slug, Model model) {
        return listPage(slug, new ProjectForm(null, null), model);
    }

    @PostMapping
    String create(@PathVariable String slug, @Valid @ModelAttribute("form") ProjectForm form,
                  BindingResult errors, Model model, HttpServletResponse response) {
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return listPage(slug, form, model);
        }
        ProjectView created = projects.create(form);
        return "redirect:/o/" + slug + "/projects/" + created.id();
    }

    private String listPage(String slug, ProjectForm form, Model model) {
        model.addAttribute("slug", slug);
        model.addAttribute("projects", projects.listMine());
        model.addAttribute("mayCreate", projects.mayCreate());
        model.addAttribute("form", form);
        return "projects";
    }

    @GetMapping("/{id}")
    String view(@PathVariable String slug, @PathVariable Long id, Model model) {
        model.addAttribute("slug", slug);
        model.addAttribute("project", projects.view(id));
        return "project";
    }

    @GetMapping("/{id}/settings")
    String settings(@PathVariable String slug, @PathVariable Long id, Model model) {
        ProjectView project = projects.settings(id);
        return settingsPage(slug, project, new ProjectForm(project.name(), project.description()), model);
    }

    @PostMapping("/{id}/settings")
    String update(@PathVariable String slug, @PathVariable Long id, @Valid @ModelAttribute("form") ProjectForm form,
                  BindingResult errors, Model model, HttpServletResponse response) {
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            return settingsPage(slug, projects.settings(id), form, model);
        }
        projects.update(id, form);
        return "redirect:/o/" + slug + "/projects/" + id + "/settings";
    }

    @PostMapping("/{id}/archive")
    String archive(@PathVariable String slug, @PathVariable Long id) {
        projects.archive(id);
        return "redirect:/o/" + slug + "/projects/" + id + "/settings";
    }

    @PostMapping("/{id}/restore")
    String restore(@PathVariable String slug, @PathVariable Long id) {
        projects.restore(id);
        return "redirect:/o/" + slug + "/projects/" + id + "/settings";
    }

    private String settingsPage(String slug, ProjectView project, ProjectForm form, Model model) {
        model.addAttribute("slug", slug);
        model.addAttribute("project", project);
        model.addAttribute("form", form);
        return "project-settings";
    }
}
