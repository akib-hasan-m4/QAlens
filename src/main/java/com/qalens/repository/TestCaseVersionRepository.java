package com.qalens.repository;

import com.qalens.domain.TestCaseVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Append-only: there is deliberately no update or delete here. Reach versions through an authorized case id. */
public interface TestCaseVersionRepository extends JpaRepository<TestCaseVersion, Long> {

    Optional<TestCaseVersion> findByIdAndCaseId(Long id, Long caseId);

    Optional<TestCaseVersion> findByCaseIdAndVersionNo(Long caseId, int versionNo);

    List<TestCaseVersion> findByCaseIdOrderByVersionNoDesc(Long caseId);

    @Query("select coalesce(max(v.versionNo), 0) from TestCaseVersion v where v.caseId = :caseId")
    int latestVersionNo(@Param("caseId") Long caseId);

    @Query("select v from TestCaseVersion v where v.caseId in :caseIds and v.id in "
            + "(select c.currentVersionId from TestCase c where c.id in :caseIds)")
    List<TestCaseVersion> currentVersionsOf(@Param("caseIds") List<Long> caseIds);
}
