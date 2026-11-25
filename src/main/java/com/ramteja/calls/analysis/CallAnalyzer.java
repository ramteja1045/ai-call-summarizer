package com.ramteja.calls.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ramteja.calls.llm.LlmClient;
import com.ramteja.calls.llm.LlmException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Turns a transcript into a validated {@link CallAnalysis}. LLM output is treated as untrusted:
 * it is parsed, schema-validated and checked against the rubric. One repair attempt is made on
 * bad output before giving up.
 */
@Component
public class CallAnalyzer {

    private final LlmClient llm;
    private final PromptBuilder prompts;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final RubricProperties rubric;

    public CallAnalyzer(LlmClient llm, PromptBuilder prompts, ObjectMapper mapper,
                        Validator validator, RubricProperties rubric) {
        this.llm = llm;
        this.prompts = prompts;
        this.mapper = mapper;
        this.validator = validator;
        this.rubric = rubric;
    }

    public CallAnalysis analyze(String transcript) {
        String system = prompts.systemPrompt();
        String user = prompts.userPrompt(transcript);
        String raw = llm.completeJson(system, user);
        try {
            return parseAndValidate(raw);
        } catch (InvalidAnalysisException first) {
            String repair = user + "\n\nYour previous reply was invalid: " + first.getMessage()
                    + "\nReturn corrected JSON only.";
            return parseAndValidate(llm.completeJson(system, repair));
        }
    }

    CallAnalysis parseAndValidate(String raw) {
        CallAnalysis analysis;
        try {
            analysis = mapper.readValue(stripCodeFences(raw), CallAnalysis.class);
        } catch (JsonProcessingException e) {
            throw new InvalidAnalysisException("not valid JSON for the required shape");
        }
        var violations = validator.validate(analysis);
        if (!violations.isEmpty()) {
            throw new InvalidAnalysisException(violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; ")));
        }
        var expected = rubric.criteria().stream().map(RubricProperties.Criterion::name).collect(Collectors.toSet());
        var returned = analysis.criteria().stream().map(CallAnalysis.CriterionScore::name).collect(Collectors.toSet());
        if (!returned.containsAll(expected)) {
            throw new InvalidAnalysisException("missing rubric criteria; expected " + expected + " but got " + returned);
        }
        return analysis;
    }

    private static String stripCodeFences(String raw) {
        String s = raw.strip();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        return s;
    }

    public static class InvalidAnalysisException extends LlmException {
        public InvalidAnalysisException(String message) { super(message); }
    }
}
