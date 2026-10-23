package com.qalens.web;

import com.qalens.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
        model.addAttribute("slug", slug);
        model.addAttribute("projects", projects.listMine());
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
        model.addAttribute("slug", slug);
        model.addAttribute("project", projects.settings(id));
        return "project-settings";
    }

    /** A malformed id is just another id that does not exist. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String malformedId(Model model) {
        model.addAttribute("status", HttpStatus.NOT_FOUND.value());
        return "error";
    }
}
