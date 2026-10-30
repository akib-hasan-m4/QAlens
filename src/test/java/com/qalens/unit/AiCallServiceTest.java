package com.qalens.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.qalens.domain.AiCallLog;
import com.qalens.repository.AiCallLogRepository;
import com.qalens.service.ai.AiCallService;
import com.qalens.service.ai.AiCallView;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiCallServiceTest {

    private final AiCallLogRepository repo = mock(AiCallLogRepository.class);
    private final AiCallService service = new AiCallService(repo);

    private static AiCallLog row(String status, String outputRef, String error) {
        return new AiCallLog(1L, "TEST_CASE_GENERATION", "m", "case-gen-v2", "a".repeat(64), 4100, 10, 20, status,
                error, outputRef);
    }

    @Test
    void draftCountsAreReadFromTheOutputRef() {
        when(repo.findTop20ByProjectIdOrderByCreatedAtDescIdDesc(1L)).thenReturn(List.of(
                row("OK", "drafts=4,dropped=1", null), row("ERROR", null, "connection refused"),
                row("OK", "something else", null), row("OK", "candidates=5,embedded=2", null)));

        List<AiCallView> views = service.recent(1L);

        assertThat(views).extracting(AiCallView::drafts).containsExactly(4, null, null, null);
        assertThat(views).extracting(AiCallView::dropped).containsExactly(1, null, null, null);
        assertThat(views).extracting(AiCallView::candidates).containsExactly(null, null, null, 5);
        assertThat(views.get(1).errorMessage()).isEqualTo("connection refused");
        assertThat(views.get(0).latencyMs()).isEqualTo(4100);
    }
}
