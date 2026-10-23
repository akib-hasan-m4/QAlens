package com.qalens.tenant;

import com.qalens.domain.Organization;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/** The organisation resolved for the current request; empty on requests outside {@code /o/{slug}}. */
@Component
@RequestScope
public class TenantContext {

    private Long id;
    private String slug;
    private String name;

    public void set(Organization org) {
        this.id = org.getId();
        this.slug = org.getSlug();
        this.name = org.getName();
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }
}
