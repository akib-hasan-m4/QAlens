package com.qalens.repository;

import com.qalens.domain.TestSuite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Project-scoped, like {@link RequirementRepository}: inherited id-only finders must not be called. */
public interface TestSuiteRepository extends JpaRepository<TestSuite, Long> {

    Optional<TestSuite> findByIdAndProjectId(Long id, Long projectId);

    List<TestSuite> findByProjectIdOrderByPositionAscIdAsc(Long projectId);

    int countByProjectIdAndParentId(Long projectId, Long parentId);
}
