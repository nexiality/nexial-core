package org.nexial.core.tms.model;

/**
 * Enum to store the various keywords used in BDD
 */
public enum BDDKeywords {
    FEATURE("FEATURE"),
    RULE("RULE"),
    GIVEN("GIVEN"),
    SCENARIO("SCENARIO"),
    EXAMPLE("EXAMPLE"),
    WHEN("WHEN"),
    THEN("THEN"),
    AND("AND"),
    BUT("BUT"),
    BACKGROUND("BACKGROUND"),
    SCENARIO_OUTLINE("SCENARIO OUTLINE");

    private final String keyword;

    BDDKeywords(String keyword) { this.keyword = keyword; }

    public String getKeyword() { return keyword; }
}
