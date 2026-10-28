package com.qalens.repository;

import com.qalens.domain.CaseVersionEmbedding;
import com.qalens.domain.TestCaseVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Embeddings reach a project through version, case and suite, so every query joins the suite and checks the project. */
public interface CaseVersionEmbeddingRepository extends JpaRepository<CaseVersionEmbedding, Long> {

    /** Current versions of the project's cases that have no embedding under {@code model} yet. */
    @Query("select v from TestCaseVersion v, TestCase c, TestSuite s where c.currentVersionId = v.id "
            + "and c.suiteId = s.id and s.projectId = :projectId and not exists "
            + "(select e.id from CaseVersionEmbedding e where e.caseVersionId = v.id and e.model = :model) "
            + "order by v.id")
    List<TestCaseVersion> currentVersionsWithoutEmbedding(@Param("projectId") Long projectId,
                                                          @Param("model") String model);

    /**
     * The project's cases whose current version is closest to {@code query} by cosine similarity, leaving out the
     * cases already linked to the requirement. {@code query} is a pgvector literal such as {@code [0.1,0.2]}.
     */
    @Query(value = """
            select c.id as "caseId", v.version_no as "versionNo", v.title as "title", v.priority as "priority",
                   s.name as "suiteName", 1 - (e.embedding <=> cast(:query as vector)) as "similarity"
            from case_version_embedding e
            join test_case_version v on v.id = e.case_version_id
            join test_case c on c.current_version_id = v.id
            join test_suite s on s.id = c.suite_id
            where s.project_id = :projectId and e.model = :model and c.status = 'ACTIVE'
              and not exists (select 1 from requirement_test_case l
                              where l.test_case_id = c.id and l.requirement_id = :requirementId)
              and 1 - (e.embedding <=> cast(:query as vector)) >= :minSimilarity
            order by e.embedding <=> cast(:query as vector), c.id
            limit :limit""", nativeQuery = true)
    List<ImpactCandidateRow> nearest(@Param("projectId") Long projectId, @Param("requirementId") Long requirementId,
                                     @Param("model") String model, @Param("query") String query,
                                     @Param("minSimilarity") double minSimilarity, @Param("limit") int limit);
}
