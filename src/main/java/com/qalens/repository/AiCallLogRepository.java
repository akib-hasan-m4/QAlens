package com.qalens.repository;

import com.qalens.domain.AiCallLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant-scoped through {@code project_id}: read rows only by project, after the project has been authorised. */
public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

    List<AiCallLog> findTop20ByProjectIdOrderByCreatedAtDescIdDesc(Long projectId);
}
