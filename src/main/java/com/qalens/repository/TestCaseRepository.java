package com.qalens.repository;

import com.qalens.domain.TestCase;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** A case belongs to a project through its suite, so every lookup joins the suite and checks the project. */
public interface TestCaseRepository extends JpaRepository<TestCase, Long> {

    @Query("select c from TestCase c, TestSuite s where c.suiteId = s.id and c.id = :id and s.projectId = :projectId")
    Optional<TestCase> findInProject(@Param("id") Long id, @Param("projectId") Long projectId);

    List<TestCase> findBySuiteIdOrderByIdAsc(Long suiteId);
}
