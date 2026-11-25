package com.ramteja.calls;

import com.ramteja.calls.api.CallController;
import com.ramteja.calls.domain.Call;
import com.ramteja.calls.domain.CallRepository;
import com.ramteja.calls.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "analysis.retry-interval-ms=3600000")
@Testcontainers
class CallFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Scripted LLM: returns queued replies in order, so tests are fast and deterministic. */
    static class ScriptedLlm implements LlmClient {
        final Deque<String> replies = new ArrayDeque<>();
        @Override
        public synchronized String completeJson(String system, String user) {
            return replies.isEmpty() ? "not json" : replies.poll();
        }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary ScriptedLlm scriptedLlm() { return new ScriptedLlm(); }
    }

    static final String GOOD = """
            {"summary":"Agent quoted an auto policy and booked a follow-up.","overallScore":78,
             "criteria":[{"name":"Rapport","score":8,"evidence":"Thanks for calling, Maria"},
                         {"name":"Discovery","score":7,"evidence":"How many drivers are in the household?"},
                         {"name":"Value","score":8,"evidence":"explained liability limits"},
                         {"name":"Objection Handling","score":7,"evidence":"addressed price concern"},
                         {"name":"Close","score":9,"evidence":"Thursday at 3pm"}],
             "actionItems":["Ask about bundling home insurance"],
             "customerSentiment":"positive"}
            """;

    static final String TRANSCRIPT = "Agent: Thanks for calling, Maria. Customer: I need auto insurance for two cars...";

    @Autowired CallController controller;
    @Autowired CallRepository calls;
    @Autowired ScriptedLlm llm;

    @BeforeEach
    void reset() { llm.replies.clear(); }

    @Test
    void analyzes_call_in_background_and_stores_validated_result() {
        llm.replies.add(GOOD);
        var submitted = controller.submit(new CallController.SubmitCallRequest("maria", TRANSCRIPT));
        assertThat(submitted.status()).isEqualTo(Call.Status.PENDING);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Call call = calls.findById(submitted.id()).orElseThrow();
            assertThat(call.getStatus()).isEqualTo(Call.Status.ANALYZED);
            assertThat(call.getScore()).isEqualTo(78);
        });
    }

    @Test
    void repairs_once_when_model_returns_bad_json() {
        llm.replies.add("```json\n{\"summary\": \"oops\"}\n```");   // missing fields
        llm.replies.add(GOOD);                                    // corrected on repair
        var submitted = controller.submit(new CallController.SubmitCallRequest("devon", TRANSCRIPT));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(calls.findById(submitted.id()).orElseThrow().getStatus()).isEqualTo(Call.Status.ANALYZED));
    }

    @Test
    void marks_failed_when_model_output_stays_invalid() {
        llm.replies.add("not json");
        llm.replies.add("{\"overallScore\": 250}");
        var submitted = controller.submit(new CallController.SubmitCallRequest("sam", TRANSCRIPT));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Call call = calls.findById(submitted.id()).orElseThrow();
            assertThat(call.getStatus()).isEqualTo(Call.Status.FAILED);
            assertThat(call.getAttempts()).isEqualTo(1);
        });
    }
}
