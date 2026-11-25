package com.ramteja.calls.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ramteja.calls.analysis.CallAnalysisWorker.CallSubmitted;
import com.ramteja.calls.domain.Call;
import com.ramteja.calls.domain.CallRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/calls")
public class CallController {

    public record SubmitCallRequest(@NotBlank @Size(max = 100) String agentName,
                                    @NotBlank @Size(min = 20, max = 200_000) String transcript) { }

    public record CallResponse(UUID id, String agentName, Call.Status status, String summary, Integer score,
                               JsonNode analysis, String failureReason, Instant createdAt, Instant analyzedAt) { }

    private final CallRepository calls;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper;

    public CallController(CallRepository calls, ApplicationEventPublisher events, ObjectMapper mapper) {
        this.calls = calls;
        this.events = events;
        this.mapper = mapper;
    }

    /** Accepts a transcript and returns 202 right away; poll GET /api/calls/{id} for the result. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public CallResponse submit(@Valid @RequestBody SubmitCallRequest req) {
        Call call = calls.save(new Call(req.agentName(), req.transcript()));
        events.publishEvent(new CallSubmitted(call.getId()));
        return toResponse(call);
    }

    @GetMapping("/{id}")
    public CallResponse get(@PathVariable UUID id) {
        return calls.findById(id).map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Call " + id + " not found"));
    }

    @GetMapping
    public Page<CallResponse> byAgent(@RequestParam String agentName,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return calls.findByAgentNameOrderByCreatedAtDesc(agentName, PageRequest.of(page, Math.min(size, 100)))
                .map(this::toResponse);
    }

    @GetMapping("/leaderboard")
    public List<CallRepository.AgentStats> leaderboard() {
        return calls.agentLeaderboard(Call.Status.ANALYZED);
    }

    private CallResponse toResponse(Call c) {
        JsonNode analysis = null;
        if (c.getAnalysisJson() != null) {
            try {
                analysis = mapper.readTree(c.getAnalysisJson());
            } catch (Exception ignored) {
                // Stored JSON was validated on write; a parse failure here would be a data bug.
            }
        }
        return new CallResponse(c.getId(), c.getAgentName(), c.getStatus(), c.getSummary(), c.getScore(),
                analysis, c.getFailureReason(), c.getCreatedAt(), c.getAnalyzedAt());
    }
}
