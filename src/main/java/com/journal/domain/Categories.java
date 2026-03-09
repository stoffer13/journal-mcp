package com.journal.domain;

import java.util.Map;

public final class Categories {
    public static final String TECH_DEBT = "tech_debt";
    public static final String TEAM_EVAL = "team_eval";
    public static final String FEATURE_REFINEMENT = "feature_refinement";
    public static final String TODO = "todo";
    public static final String DECISION = "decision";
    public static final String OBSERVATION = "observation";
    public static final String BLOCKER = "blocker";

    public static final Map<String, String> DESCRIPTIONS = Map.of(
        TECH_DEBT, "Code quality, shortcuts, things to refactor",
        TEAM_EVAL, "Team member performance, behavior, growth",
        FEATURE_REFINEMENT, "Feature ideas, requirement changes, UX thoughts",
        TODO, "Concrete action items",
        DECISION, "Architectural, product, or process decisions",
        OBSERVATION, "General insights and patterns",
        BLOCKER, "Things actively blocking progress"
    );

    private Categories() {}
}
