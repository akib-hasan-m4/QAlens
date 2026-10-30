package com.qalens.repository;

import com.qalens.domain.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Tenant-scoped outside of login: administration reaches users through org-aware finders only, and an ArchUnit
 * rule forbids the inherited unconstrained ones.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @EntityGraph(attributePaths = "org")
    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select u.displayName from AppUser u where u.id = :id")
    Optional<String> findDisplayNameById(@Param("id") Long id);

    Optional<AppUser> findByIdAndOrgId(Long id, Long orgId);

    boolean existsByIdAndOrgIdAndOrgAdminTrueAndActiveTrue(Long id, Long orgId);

    @Query("select u.id as id, u.email as email, u.displayName as displayName, u.active as active, "
            + "u.orgAdmin as orgAdmin, "
            + "(select count(m) from UserProjectRole m where m.id.userId = u.id) as projectCount "
            + "from AppUser u where u.org.id = :orgId order by lower(u.displayName), u.id")
    List<UserRow> findRowsByOrgId(@Param("orgId") Long orgId);

    /** Active users of the organisation who hold no role on the project yet. */
    @Query("select u from AppUser u where u.org.id = :orgId and u.active = true and not exists "
            + "(select m from UserProjectRole m where m.id.userId = u.id and m.id.projectId = :projectId) "
            + "order by lower(u.displayName), u.id")
    List<AppUser> findAddableToProject(@Param("orgId") Long orgId, @Param("projectId") Long projectId);
}
