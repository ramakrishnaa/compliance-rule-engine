package com.example.compliance.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects the issues found while loading and evaluating one batch of input. Not thread-safe. */
public class Diagnostics {

    private final List<Issue> issues = new ArrayList<>();

    public void warn(String source, String message) {
        issues.add(new Issue(Issue.Level.WARNING, source, message));
    }

    public List<Issue> issues() {
        return Collections.unmodifiableList(issues);
    }

    public boolean isEmpty() {
        return issues.isEmpty();
    }
}
