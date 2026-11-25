package com.ramteja.calls.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ramteja.calls.domain.Call;
import com.ramteja.calls.domain.CallRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Runs analysis in the background so the upload request returns immediately.
 * The event fires only AFTER the call row commits, so the worker never reads a row that isn't there.
 * A scheduled sweep retries failed calls up to a limit.
 */
@Component
@EnableScheduling
public class CallAnalysisWorker {

    public record CallSubmitted(UUID callId) { }

    private static final Logger log = LoggerFactory.getLogger(CallAnalysisWorker.class);

    private final CallRepository calls;
    private final CallAnalyzer analyzer;
    private final ObjectMapper mapper;
    private final int maxAttempts;

    public CallAnalysisWorker(CallRepository calls, CallAnalyzer analyzer, ObjectMapper mapper,
                              @Value("${analysis.max-attempts:3}") int maxAttempts) {
        this.calls = calls;
        this.analyzer = analyzer;
        this.mapper = mapper;
        this.maxAttempts = maxAttempts;
    }

    @Async
    @TransactionalEventListener
    public void onSubmitted(CallSubmitted event) {
        process(event.callId());
    }

    @Scheduled(fixedDelayString = "${analysis.retry-interval-ms:60000}")
    public void retryFailed() {
        calls.findRetryable(Call.Status.FAILED, maxAttempts).forEach(c -> process(c.getId()));
    }

    void process(UUID callId) {
        Call call = calls.findById(callId).orElse(null);
        if (call == null || call.getStatus() == Call.Status.ANALYZED) {
            return;
        }
        call.recordAttempt();
        try {
            CallAnalysis analysis = analyzer.analyze(call.getTranscript());
            call.markAnalyzed(analysis.summary(), analysis.overallScore(), mapper.writeValueAsString(analysis));
        } catch (Exception e) {
            log.warn("Analysis failed for call {} (attempt {}): {}", callId, call.getAttempts(), e.getMessage());
            call.markFailed(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        calls.save(call);
    }
}
