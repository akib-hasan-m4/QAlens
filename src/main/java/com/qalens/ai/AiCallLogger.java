package com.qalens.ai;

import com.qalens.domain.AiCallLog;
import com.qalens.repository.AiCallLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes {@code ai_call_log} rows in their own transaction so a failed call is still recorded. */
@Component
public class AiCallLogger {

    static final int MAX_ERROR = 500;

    private final AiCallLogRepository logs;

    AiCallLogger(AiCallLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String feature, String model, String promptVersion, String inputHash, long latencyMs,
            Integer tokensIn, Integer tokensOut, String status, String error, String outputRef) {
        String message = error == null ? null : error.length() <= MAX_ERROR ? error : error.substring(0, MAX_ERROR);
        logs.save(new AiCallLog(feature, model, promptVersion, inputHash, (int) Math.min(latencyMs, Integer.MAX_VALUE),
                tokensIn, tokensOut, status, message, outputRef));
    }
}
