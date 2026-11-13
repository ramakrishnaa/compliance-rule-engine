package com.example.compliance.engine;

/** Result of applying one rule to one transaction. */
public record Outcome(Status status, String reason) {

    public enum Status { BREACHED, NOT_BREACHED, NOT_EVALUABLE }

    private static final Outcome BREACHED = new Outcome(Status.BREACHED, null);
    private static final Outcome NOT_BREACHED = new Outcome(Status.NOT_BREACHED, null);

    public static Outcome of(boolean breached) {
        return breached ? BREACHED : NOT_BREACHED;
    }

    /** The rule could not be applied (missing or unusable data); it is not counted as breached. */
    public static Outcome notEvaluable(String reason) {
        return new Outcome(Status.NOT_EVALUABLE, reason);
    }

    public boolean isBreached() {
        return status == Status.BREACHED;
    }
}
