package com.qalens.repository;

import com.qalens.domain.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Tenant-scoped: every lookup carries the organisation. Unconstrained inherited finders such as
 * {@code findById} must not be called; an ArchUnit rule enforces that.
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByIdAndOrgId(Long id, Long orgId);

    Optional<Project> findByOrgIdAndProjKey(Long orgId, String projKey);

    @Query("select p.id as id, p.projKey as projKey, p.name as name, p.description as description, "
            + "p.archived as archived, r.name as roleName "
            + "from Project p, UserProjectRole u join u.role r "
            + "where u.id.projectId = p.id and u.id.userId = :userId and p.org.id = :orgId "
            + "order by p.name")
    List<ProjectRow> findForUser(@Param("userId") Long userId, @Param("orgId") Long orgId);

    @Query("select p.id as id, p.projKey as projKey, p.name as name, p.description as description, "
            + "p.archived as archived, r.name as roleName "
            + "from Project p, UserProjectRole u join u.role r "
            + "where u.id.projectId = p.id and u.id.userId = :userId and p.org.id = :orgId and p.id = :projectId")
    Optional<ProjectRow> findForUser(@Param("userId") Long userId, @Param("orgId") Long orgId,
                                     @Param("projectId") Long projectId);
}
