package com.ramteja.calls.analysis;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

/** The structured result we require the LLM to return. Validated before anything is stored. */
public record CallAnalysis(
        @NotBlank @Size(max = 2000) String summary,
        @Min(0) @Max(100) int overallScore,
        @NotEmpty List<@Valid CriterionScore> criteria,
        @NotNull List<@NotBlank String> actionItems,
        @NotBlank @Pattern(regexp = "positive|neutral|negative") String customerSentiment) {

    public record CriterionScore(@NotBlank String name,
                                 @Min(0) @Max(10) int score,
                                 @NotBlank @Size(max = 500) String evidence) { }
}
