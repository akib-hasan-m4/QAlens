package com.qalens.web;

import com.qalens.service.QalensUserDetails;
import com.qalens.tenant.TenantContext;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final TenantContext tenant;

    public HomeController(TenantContext tenant) {
        this.tenant = tenant;
    }

    @GetMapping("/")
    String root(@AuthenticationPrincipal QalensUserDetails user) {
        return "redirect:/o/" + user.getOrgSlug() + "/";
    }

    /** The tenant filter has already guaranteed that the slug is the caller's own organisation. */
    @GetMapping("/o/{slug}/")
    String home(@AuthenticationPrincipal QalensUserDetails user, Model model) {
        model.addAttribute("user", user);
        model.addAttribute("tenant", tenant);
        return "home";
    }
}
