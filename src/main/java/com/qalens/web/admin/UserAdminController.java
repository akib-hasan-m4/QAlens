package com.qalens.web.admin;

import com.qalens.common.RuleViolationException;
import com.qalens.service.user.NewUserForm;
import com.qalens.service.user.UserAdminService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** The organisation in the URL has already been checked by the tenant filter; authorization is in the service. */
@Controller
@RequestMapping("/o/{slug}/admin/users")
public class UserAdminController {

    private final UserAdminService users;

    public UserAdminController(UserAdminService users) {
        this.users = users;
    }

    @GetMapping
    String list(@PathVariable String slug, Model model) {
        return listPage(slug, new NewUserForm(null, null, null, false), model);
    }

    @PostMapping
    String create(@PathVariable String slug, @Valid @ModelAttribute("form") NewUserForm form, BindingResult errors,
                  Model model, HttpServletResponse response, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                String name = users.create(form);
                redirect.addFlashAttribute("notice", name + " can now sign in with the password you set.");
                return "redirect:/o/" + slug + "/admin/users";
            } catch (RuleViolationException e) {
                errors.rejectValue("email", "taken", e.getMessage());
            }
        }
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return listPage(slug, form, model);
    }

    @PostMapping("/{id}/active")
    String setActive(@PathVariable String slug, @PathVariable Long id, @RequestParam boolean active,
                     RedirectAttributes redirect) {
        try {
            String name = users.setActive(id, active);
            redirect.addFlashAttribute("notice", name + (active ? " was reactivated." : " was deactivated and signed out."));
        } catch (RuleViolationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/o/" + slug + "/admin/users";
    }

    @PostMapping("/{id}/org-admin")
    String setOrgAdmin(@PathVariable String slug, @PathVariable Long id, @RequestParam boolean orgAdmin,
                       RedirectAttributes redirect) {
        try {
            String name = users.setOrgAdmin(id, orgAdmin);
            redirect.addFlashAttribute("notice", name + (orgAdmin ? " is now an organisation admin."
                    : " is no longer an organisation admin."));
        } catch (RuleViolationException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/o/" + slug + "/admin/users";
    }

    private String listPage(String slug, NewUserForm form, Model model) {
        model.addAttribute("slug", slug);
        model.addAttribute("users", users.list());
        model.addAttribute("form", form);
        return "admin-users";
    }
}
