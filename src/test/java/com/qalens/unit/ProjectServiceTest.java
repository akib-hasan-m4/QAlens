package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qalens.common.ResourceNotFoundException;
import com.qalens.domain.Organization;
import com.qalens.domain.Project;
import com.qalens.domain.Role;
import com.qalens.domain.UserProjectRole;
import com.qalens.repository.OrganizationRepository;
import com.qalens.repository.ProjectRepository;
import com.qalens.repository.ProjectRow;
import com.qalens.repository.RoleRepository;
import com.qalens.repository.UserProjectRoleRepository;
import com.qalens.security.ProjectAccess;
import com.qalens.service.project.ProjectForm;
import com.qalens.service.project.ProjectService;
import com.qalens.service.project.ProjectView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProjectServiceTest {

    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final OrganizationRepository orgs = mock(OrganizationRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserProjectRoleRepository memberships = mock(UserProjectRoleRepository.class);
    private final ProjectAccess access = mock(ProjectAccess.class);
    private final ProjectService service = new ProjectService(projects, orgs, roles, memberships, access);

    @BeforeEach
    void signIn() {
        ServiceTestSupport.signIn(7, 1);
    }

    @AfterEach
    void signOut() {
        ServiceTestSupport.signOut();
    }

    private void stubCreate() {
        Role admin = mock(Role.class);
        when(admin.getName()).thenReturn("ADMIN");
        when(roles.findByName("ADMIN")).thenReturn(Optional.of(admin));
        when(orgs.getReferenceById(1L)).thenReturn(mock(Organization.class));
        when(projects.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createUsesTheFirstFreeKeyAndMakesTheCreatorAdmin() {
        stubCreate();
        when(projects.findByOrgIdAndProjKey(1L, "PG")).thenReturn(Optional.of(mock(Project.class)));
        when(projects.findByOrgIdAndProjKey(1L, "PG2")).thenReturn(Optional.empty());

        ProjectView created = service.create(new ProjectForm("Payment Gateway", "d"));

        assertThat(created.key()).isEqualTo("PG2");
        assertThat(created.myRole()).isEqualTo("ADMIN");
        verify(memberships).save(any(UserProjectRole.class));
    }

    @Test
    void createFailsWhenEveryKeyIsTaken() {
        stubCreate();
        when(projects.findByOrgIdAndProjKey(any(), any())).thenReturn(Optional.of(mock(Project.class)));

        assertThatThrownBy(() -> service.create(new ProjectForm("Payment Gateway", null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateArchiveAndRestoreChangeTheProject() {
        Project p = new Project(mock(Organization.class), "KEY", "Old", "old");
        when(projects.findByIdAndOrgId(5L, 1L)).thenReturn(Optional.of(p));

        service.update(5L, new ProjectForm("New name", "new"));
        assertThat(p.getName()).isEqualTo("New name");
        assertThat(p.getProjKey()).isEqualTo("KEY");

        service.archive(5L);
        assertThat(p.isArchived()).isTrue();
        service.restore(5L);
        assertThat(p.isArchived()).isFalse();
    }

    @Test
    void aProjectOfAnotherOrganisationIsNotFound() {
        when(projects.findByIdAndOrgId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archive(5L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.update(5L, new ProjectForm("x", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listAndViewMapRowsAndHideProjectsWithoutARole() {
        ProjectRow row = mock(ProjectRow.class);
        when(row.getId()).thenReturn(5L);
        when(row.getProjKey()).thenReturn("DEMO");
        when(row.getName()).thenReturn("Demo");
        when(row.getArchived()).thenReturn(null);
        when(row.getRoleName()).thenReturn("TESTER");
        when(projects.findForUser(7L, 1L)).thenReturn(List.of(row));
        when(projects.findForUser(7L, 1L, 5L)).thenReturn(Optional.of(row));
        when(projects.findForUser(7L, 1L, 6L)).thenReturn(Optional.empty());

        assertThat(service.listMine()).extracting(ProjectView::key).containsExactly("DEMO");
        assertThat(service.view(5L).archived()).isFalse();
        assertThat(service.settings(5L).myRole()).isEqualTo("TESTER");
        assertThatThrownBy(() -> service.view(6L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void mayCreateAsksTheAccessBean() {
        when(access.canCreateProject()).thenReturn(true);
        assertThat(service.mayCreate()).isTrue();
    }
}
