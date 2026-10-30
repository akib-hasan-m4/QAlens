package com.qalens.security;

import com.qalens.repository.AppUserRepository;
import org.springframework.stereotype.Component;

/**
 * Used from {@code @PreAuthorize("@orgAccess.isAdmin()")}. Read from the database on every call, so revoking the
 * right or deactivating the account takes effect on the next request, not at the next login.
 */
@Component("orgAccess")
public class OrgAccess {

    private final AppUserRepository users;

    public OrgAccess(AppUserRepository users) {
        this.users = users;
    }

    public boolean isAdmin() {
        QalensUserDetails user = ProjectAccess.currentUser();
        return users.existsByIdAndOrgIdAndOrgAdminTrueAndActiveTrue(user.getId(), user.getOrgId());
    }
}
