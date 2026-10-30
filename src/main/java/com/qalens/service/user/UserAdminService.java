package com.qalens.service.user;

import com.qalens.common.ResourceNotFoundException;
import com.qalens.common.RuleViolationException;
import com.qalens.domain.AppUser;
import com.qalens.repository.AppUserRepository;
import com.qalens.repository.OrganizationRepository;
import com.qalens.security.OrgAccess;
import com.qalens.security.ProjectAccess;
import com.qalens.security.QalensUserDetails;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Users of the caller's organisation. A user of another organisation is a 404. Admins cannot deactivate
 * themselves or drop their own admin right, so an organisation always keeps at least one active admin.
 */
@Service
public class UserAdminService {

    private final AppUserRepository users;
    private final OrganizationRepository orgs;
    private final PasswordEncoder encoder;
    private final SessionRegistry sessions;
    private final OrgAccess access;

    public UserAdminService(AppUserRepository users, OrganizationRepository orgs, PasswordEncoder encoder,
                            SessionRegistry sessions, OrgAccess access) {
        this.users = users;
        this.orgs = orgs;
        this.encoder = encoder;
        this.sessions = sessions;
        this.access = access;
    }

    /** Presentation hint for the navigation; every other method enforces the same rule. */
    @Transactional(readOnly = true)
    public boolean mayManage() {
        return access.isAdmin();
    }

    @PreAuthorize("@orgAccess.isAdmin()")
    @Transactional(readOnly = true)
    public List<UserView> list() {
        QalensUserDetails me = ProjectAccess.currentUser();
        return users.findRowsByOrgId(me.getOrgId()).stream()
                .map(r -> new UserView(r.getId(), r.getEmail(), r.getDisplayName(),
                        Boolean.TRUE.equals(r.getActive()), Boolean.TRUE.equals(r.getOrgAdmin()),
                        r.getProjectCount() == null ? 0 : r.getProjectCount(), r.getId().equals(me.getId())))
                .toList();
    }

    @PreAuthorize("@orgAccess.isAdmin()")
    @Transactional
    public String create(NewUserForm form) {
        if (users.existsByEmailIgnoreCase(form.email())) {
            throw new RuleViolationException("This email address is already registered.");
        }
        AppUser user = new AppUser(orgs.getReferenceById(ProjectAccess.currentUser().getOrgId()), form.email(),
                form.displayName(), encoder.encode(form.password()), true);
        user.setOrgAdmin(form.orgAdmin());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // a concurrent request registered the same email between the check and the insert
            throw new RuleViolationException("This email address is already registered.");
        }
        return user.getDisplayName();
    }

    /** Deactivating also ends the user's open sessions; they cannot sign in again until reactivated. */
    @PreAuthorize("@orgAccess.isAdmin()")
    @Transactional
    public String setActive(Long userId, boolean active) {
        AppUser user = otherUser(userId, "You cannot deactivate your own account.");
        user.setActive(active);
        if (!active) {
            endSessions(userId);
        }
        return user.getDisplayName();
    }

    @PreAuthorize("@orgAccess.isAdmin()")
    @Transactional
    public String setOrgAdmin(Long userId, boolean orgAdmin) {
        AppUser user = otherUser(userId, "You cannot change your own administrator rights.");
        user.setOrgAdmin(orgAdmin);
        return user.getDisplayName();
    }

    private AppUser otherUser(Long userId, String selfMessage) {
        QalensUserDetails me = ProjectAccess.currentUser();
        AppUser user = users.findByIdAndOrgId(userId, me.getOrgId()).orElseThrow(ResourceNotFoundException::new);
        if (user.getId().equals(me.getId())) {
            throw new RuleViolationException(selfMessage);
        }
        return user;
    }

    private void endSessions(Long userId) {
        sessions.getAllPrincipals().stream()
                .filter(p -> p instanceof QalensUserDetails u && u.getId().equals(userId))
                .flatMap(p -> sessions.getAllSessions(p, false).stream())
                .forEach(SessionInformation::expireNow);
    }
}
