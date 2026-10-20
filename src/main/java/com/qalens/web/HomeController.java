package com.qalens.web;

import com.qalens.service.QalensUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class HomeController {

    @GetMapping("/")
    String root(@AuthenticationPrincipal QalensUserDetails user) {
        return "redirect:/o/" + user.orgSlug() + "/";
    }

    @GetMapping("/o/{slug}/")
    String home(@PathVariable String slug, @AuthenticationPrincipal QalensUserDetails user, Model model) {
        if (!user.orgSlug().equals(slug)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("user", user);
        return "home";
    }
}
