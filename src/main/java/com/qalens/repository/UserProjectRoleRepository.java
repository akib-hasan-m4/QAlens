package com.qalens.repository;

import com.qalens.domain.UserProjectRole;
import com.qalens.domain.UserProjectRoleId;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProjectRoleRepository extends JpaRepository<UserProjectRole, UserProjectRoleId> {

    @Query("select r.name from UserProjectRole u join u.role r "
            + "where u.id.userId = :userId and u.id.projectId = :projectId")
    Optional<String> findRoleName(@Param("userId") Long userId, @Param("projectId") Long projectId);

    /** True when the user holds at least one of the roles on any project of the organisation. */
    @Query("select count(u) > 0 from Project p, UserProjectRole u join u.role r "
            + "where u.id.projectId = p.id and p.org.id = :orgId and u.id.userId = :userId and r.name in :roles")
    boolean holdsRoleInOrg(@Param("userId") Long userId, @Param("orgId") Long orgId,
                           @Param("roles") Collection<String> roles);
}
