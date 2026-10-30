package com.qalens.web;

import com.qalens.service.project.ProjectService;
import com.qalens.service.project.ProjectView;
import com.qalens.security.QalensUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Model for the app shell in {@code layout/base}: who is signed in and the project named in the URL.
 * The project is read through {@link ProjectService#view}, so a project the caller holds no role on is the same
 * 404 the page itself would give.
 */
@ControllerAdvice
class ShellModelAdvice {

    private static final Pattern PROJECT_PATH = Pattern.compile("^/o/[^/]+/projects/(\\d{1,18})(?:/|$)");

    private final ProjectService projects;

    ShellModelAdvice(ProjectService projects) {
        this.projects = projects;
    }

    @ModelAttribute("shell")
    Shell shell(HttpServletRequest request, @AuthenticationPrincipal QalensUserDetails user) {
        if (user == null) {
            return new Shell(null, null, null, null);
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Matcher m = PROJECT_PATH.matcher(path);
        ProjectView project = m.find() ? projects.view(Long.valueOf(m.group(1))) : null;
        return new Shell(user.getDisplayName(), user.getOrgName(), user.getOrgSlug(), project);
    }

    public record Shell(String userName, String orgName, String orgSlug, ProjectView project) {

        public String initials() {
            if (userName == null || userName.isBlank()) {
                return "?";
            }
            return Arrays.stream(userName.trim().split("\\s+")).limit(2)
                    .map(w -> w.substring(0, 1).toUpperCase()).collect(Collectors.joining());
        }

        /** Nav visibility only; the settings endpoints enforce the same roles. */
        public boolean mayManageProject() {
            return project != null && ("ADMIN".equals(project.myRole()) || "QA_LEAD".equals(project.myRole()));
        }
    }
}
