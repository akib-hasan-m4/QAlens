package com.qalens.service.project;

import com.qalens.common.ResourceNotFoundException;
import com.qalens.common.RuleViolationException;
import com.qalens.domain.AppUser;
import com.qalens.domain.Role;
import com.qalens.domain.UserProjectRole;
import com.qalens.domain.UserProjectRoleId;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.RoleRepository;
import com.qalens.repository.UserProjectRoleRepository;
import com.qalens.security.ProjectAccess;
import com.qalens.security.QalensUserDetails;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project membership. ADMIN and QA_LEAD manage members; only an ADMIN may grant ADMIN or touch an ADMIN member,
 * so a QA_LEAD cannot promote themselves through a friend. Nobody changes their own membership, which also
 * means a project can never lose its last ADMIN: the ADMIN making the change is always another one.
 */
@Service
public class MemberService {

    /** In display order, highest first. */
    public static final List<String> ROLES = List.of("ADMIN", "QA_LEAD", "TESTER", "DEVELOPER", "VIEWER");

    private static final String ADMIN = "ADMIN";

    private final UserProjectRoleRepository memberships;
    private final AppUserRepository users;
    private final RoleRepository roles;

    public MemberService(UserProjectRoleRepository memberships, AppUserRepository users, RoleRepository roles) {
        this.memberships = memberships;
        this.users = users;
        this.roles = roles;
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD')")
    @Transactional(readOnly = true)
    public MembersView members(Long projectId) {
        QalensUserDetails me = ProjectAccess.currentUser();
        boolean iAmAdmin = ADMIN.equals(myRole(projectId));
        List<MemberView> members = memberships.findMembers(projectId).stream()
                .map(m -> {
                    boolean self = m.getUserId().equals(me.getId());
                    boolean editable = !self && (iAmAdmin || !ADMIN.equals(m.getRoleName()));
                    return new MemberView(m.getUserId(), m.getEmail(), m.getDisplayName(),
                            Boolean.TRUE.equals(m.getActive()), m.getRoleName(), self, editable);
                })
                .toList();
        List<MemberCandidate> candidates = users.findAddableToProject(me.getOrgId(), projectId).stream()
                .map(u -> new MemberCandidate(u.getId(), u.getDisplayName(), u.getEmail()))
                .toList();
        return new MembersView(members, candidates, grantable(iAmAdmin));
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD')")
    @Transactional
    public String add(Long projectId, MemberForm form) {
        Role role = grantableRole(projectId, form.role());
        AppUser user = users.findByIdAndOrgId(form.userId(), ProjectAccess.currentUser().getOrgId())
                .orElseThrow(ResourceNotFoundException::new);
        if (!user.isActive()) {
            throw new RuleViolationException(user.getDisplayName() + " is deactivated and cannot be added.");
        }
        if (memberships.existsById(new UserProjectRoleId(user.getId(), projectId))) {
            throw new RuleViolationException(user.getDisplayName() + " is already a member of this project.");
        }
        memberships.save(new UserProjectRole(user.getId(), projectId, role));
        return user.getDisplayName();
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD')")
    @Transactional
    public String changeRole(Long projectId, Long userId, String roleName) {
        UserProjectRole membership = otherMember(projectId, userId, "You cannot change your own role.");
        membership.setRole(grantableRole(projectId, roleName));
        return displayName(userId);
    }

    @PreAuthorize("@projectAccess.hasRole(#projectId, 'ADMIN', 'QA_LEAD')")
    @Transactional
    public String remove(Long projectId, Long userId) {
        UserProjectRole membership = otherMember(projectId, userId, "You cannot remove yourself from the project.");
        memberships.delete(membership);
        return displayName(userId);
    }

    /** A member of this project other than the caller, and one the caller may change. */
    private UserProjectRole otherMember(Long projectId, Long userId, String selfMessage) {
        UserProjectRole membership = memberships.findById(new UserProjectRoleId(userId, projectId))
                .orElseThrow(ResourceNotFoundException::new);
        if (userId.equals(ProjectAccess.currentUser().getId())) {
            throw new RuleViolationException(selfMessage);
        }
        if (ADMIN.equals(membership.getRole().getName()) && !ADMIN.equals(myRole(projectId))) {
            throw new AccessDeniedException("Only an ADMIN may change an ADMIN member");
        }
        return membership;
    }

    private Role grantableRole(Long projectId, String roleName) {
        if (!ROLES.contains(roleName)) {
            throw new RuleViolationException("Choose one of the project roles.");
        }
        if (ADMIN.equals(roleName) && !ADMIN.equals(myRole(projectId))) {
            throw new AccessDeniedException("Only an ADMIN may grant ADMIN");
        }
        return roles.findByName(roleName).orElseThrow();
    }

    private String myRole(Long projectId) {
        return memberships.findRoleName(ProjectAccess.currentUser().getId(), projectId)
                .orElseThrow(ResourceNotFoundException::new);
    }

    private String displayName(Long userId) {
        return users.findDisplayNameById(userId).orElse("The user");
    }

    private static List<String> grantable(boolean iAmAdmin) {
        return iAmAdmin ? ROLES : ROLES.stream().filter(r -> !ADMIN.equals(r)).toList();
    }
}
