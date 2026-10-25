package com.qalens.repository;

import com.qalens.domain.Requirement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Project-scoped: every lookup carries the project id, which the service has already authorized.
 * Inherited finders such as {@code findById} must not be called; an ArchUnit rule enforces that.
 */
public interface RequirementRepository extends JpaRepository<Requirement, Long> {

    Optional<Requirement> findByIdAndProjectId(Long id, Long projectId);

    List<Requirement> findByProjectId(Long projectId, Sort sort);

    /** {@code pattern} must come from {@code LikeEscaper.contains}; backslash is the escape character. */
    @Query("select r from Requirement r where r.projectId = :projectId and "
            + "(lower(r.title) like lower(:pattern) escape '\\' "
            + "or lower(coalesce(r.extKey, '')) like lower(:pattern) escape '\\')")
    List<Requirement> search(@Param("projectId") Long projectId, @Param("pattern") String pattern, Sort sort);
}
