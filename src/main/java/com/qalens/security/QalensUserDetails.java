package com.qalens.security;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated principal. Carries the tenant so the web layer never needs a repository, and
 * implements {@link CredentialsContainer} so the password hash is wiped once authentication succeeds.
 */
public final class QalensUserDetails implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String email;
    private final String displayName;
    private final Long orgId;
    private final String orgSlug;
    private final String orgName;
    private final boolean active;
    private String passwordHash;

    public QalensUserDetails(Long id, String email, String displayName, Long orgId, String orgSlug,
                             String orgName, String passwordHash, boolean active) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.orgId = orgId;
        this.orgSlug = orgSlug;
        this.orgName = orgName;
        this.passwordHash = passwordHash;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Long getOrgId() {
        return orgId;
    }

    public String getOrgSlug() {
        return orgSlug;
    }

    public String getOrgName() {
        return orgName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
