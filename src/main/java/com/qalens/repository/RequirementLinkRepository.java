package com.qalens.repository;

import com.qalens.domain.Requirement;
import com.qalens.domain.RequirementLink;
import com.qalens.domain.TestCase;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Both ids are authorized and project-checked by the service before a link is written; the read queries also
 * join through the project so a stray id yields nothing.
 */
public interface RequirementLinkRepository extends JpaRepository<RequirementLink, RequirementLink.Key> {

    boolean existsByRequirementIdAndTestCaseId(Long requirementId, Long testCaseId);

    @Modifying
    @Query("delete from RequirementLink l where l.requirementId = :requirementId and l.testCaseId = :testCaseId")
    int unlink(@Param("requirementId") Long requirementId, @Param("testCaseId") Long testCaseId);

    @Query("select r from Requirement r, RequirementLink l where l.requirementId = r.id "
            + "and l.testCaseId = :caseId and r.projectId = :projectId order by r.id")
    List<Requirement> requirementsOfCase(@Param("caseId") Long caseId, @Param("projectId") Long projectId);

    @Query("select c from TestCase c, RequirementLink l, TestSuite s where l.testCaseId = c.id "
            + "and c.suiteId = s.id and l.requirementId = :requirementId and s.projectId = :projectId order by c.id")
    List<TestCase> casesOfRequirement(@Param("requirementId") Long requirementId,
                                      @Param("projectId") Long projectId);
}
