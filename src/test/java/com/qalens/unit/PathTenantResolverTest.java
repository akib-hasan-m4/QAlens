package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.qalens.tenant.PathTenantResolver;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PathTenantResolverTest {

    private final PathTenantResolver resolver = new PathTenantResolver();

    private Optional<String> resolve(String uri) {
        return resolver.resolveSlug(new MockHttpServletRequest("GET", uri));
    }

    @Test
    void slugIsTheSecondPathSegment() {
        assertThat(resolve("/o/acme/")).contains("acme");
        assertThat(resolve("/o/acme")).contains("acme");
        assertThat(resolve("/o/acme/projects/14/runs/88")).contains("acme");
    }

    @Test
    void emptySlugIsReportedSoItCanBeRejected() {
        assertThat(resolve("/o/")).contains("");
    }

    @Test
    void pathsOutsideThePrefixHaveNoTenant() {
        assertThat(resolve("/login")).isEmpty();
        assertThat(resolve("/")).isEmpty();
        assertThat(resolve("/o")).isEmpty();
        assertThat(resolve("/other/acme/")).isEmpty();
    }

    @Test
    void contextPathIsIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/qalens/o/acme/");
        request.setContextPath("/qalens");
        assertThat(resolver.resolveSlug(request)).contains("acme");
    }
}
