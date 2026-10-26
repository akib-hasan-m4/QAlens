package com.qalens.repository;

import com.qalens.domain.AiCallLog;
import org.springframework.data.jpa.repository.JpaRepository;

/** Not tenant-scoped: the table records model calls, not customer data. */
public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {
}
