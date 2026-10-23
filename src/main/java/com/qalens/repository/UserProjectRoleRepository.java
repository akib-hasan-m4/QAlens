package com.qalens.repository;

import com.qalens.domain.UserProjectRole;
import com.qalens.domain.UserProjectRoleId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProjectRoleRepository extends JpaRepository<UserProjectRole, UserProjectRoleId> {

    @Query("select r.name from UserProjectRole u join u.role r "
            + "where u.id.userId = :userId and u.id.projectId = :projectId")
    Optional<String> findRoleName(@Param("userId") Long userId, @Param("projectId") Long projectId);
}
