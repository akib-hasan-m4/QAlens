package com.qalens.web.project;

import com.qalens.common.RuleViolationException;
import com.qalens.service.project.MemberForm;
import com.qalens.service.project.MemberService;
import com.qalens.service.project.ProjectService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The organisation in the URL has already been checked by the tenant filter; authorization is in the service. */
@Controller
@RequestMapping("/o/{slug}/projects/{id}/members")
public class MemberController {

    private final MemberService members;
    private final ProjectService projects;

    public MemberController(MemberService members, ProjectService projects) {
        this.members = members;
        this.projects = projects;
    }

    @GetMapping
    String list(@PathVariable String slug, @PathVariable Long id, Model model) {
        model.addAttribute("slug", slug);
        model.addAttribute("view", members.members(id));
        model.addAttribute("project", projects.view(id));
        return "project-members";
    }

    @PostMapping
    String add(@PathVariable String slug, @PathVariable Long id, @Valid @ModelAttribute MemberForm form,
               BindingResult errors, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("error", "Choose a user and a role.");
        } else {
            try {
                String name = members.add(id, form);
                redirect.addFlashAttribute("notice", name + " was added as " + form.role() + ".");
            } catch (RuleViolationException e) {
                redirect.addFlashAttribute("error", e.getMessage());
            }
        }
        return back(slug, id);
    }

    @PostMapping("/{userId}")
    String changeRole(@PathVariable String slug, @PathVariable Long id, @PathVariable Long userId,
                      @RequestParam String role, RedirectAttributes redirect) {
        try {
            String name = members.changeRole(id, userId, role);
            redirect.addFlashAttribute("notice", name + " is now " + role + ".");
        } catch (RuleViolationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return back(slug, id);
    }

    @PostMapping("/{userId}/remove")
    String remove(@PathVariable String slug, @PathVariable Long id, @PathVariable Long userId,
                  RedirectAttributes redirect) {
        try {
            String name = members.remove(id, userId);
            redirect.addFlashAttribute("notice", name + " was removed from the project.");
        } catch (RuleViolationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return back(slug, id);
    }

    private static String back(String slug, Long id) {
        return "redirect:/o/" + slug + "/projects/" + id + "/members";
    }
}
