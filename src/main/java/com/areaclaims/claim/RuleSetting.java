package com.areaclaims.claim;

/**
 * Einstellung für eine einzelne {@link RuleType} auf einem Claim: ob das Verbot scharf ist, und
 * ab welcher Mindestrolle das Verbot ignoriert wird (die Rolle selbst UND alle höheren Rollen
 * ignorieren es). Der Besitzer ignoriert immer alles, unabhängig von dieser Einstellung.
 */
public record RuleSetting(boolean enabled, ClaimRole minRoleToIgnore) {

    public static RuleSetting disabled() {
        return new RuleSetting(false, ClaimRole.COOWNER);
    }
}
