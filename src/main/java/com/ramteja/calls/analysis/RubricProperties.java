package com.ramteja.calls.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** The sales framework calls are graded against. Editable in application.yml with no code change. */
@ConfigurationProperties(prefix = "rubric")
public record RubricProperties(List<Criterion> criteria) {
    public record Criterion(String name, String description) { }
}
