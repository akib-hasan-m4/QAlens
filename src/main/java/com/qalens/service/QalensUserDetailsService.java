package com.qalens.service;

import com.qalens.domain.AppUser;
import com.qalens.repository.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QalensUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public QalensUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return new QalensUserDetails(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getOrg().getSlug(),
                user.getOrg().getName(),
                user.getPasswordHash(),
                user.isActive());
    }
}
