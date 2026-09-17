package com.marvex.util;

import java.util.Set;

/**
 * Server-side mirror of frontend/src/constants/institutions.js LIVE_INSTITUTIONS.
 * The frontend list only hides the test/sandbox institution from the production build's
 * <select> — it is not an authorization boundary. A merchant (or anyone replaying the
 * request) could otherwise POST any institutionId directly, including the sandbox
 * institution, which never moves real money and would make customer payments report
 * PAID while the merchant receives nothing.
 */
public final class PayoutInstitutions {

    /** Thrown by AccountService/AuthService/PaymentService whenever {@link #isValid} fails. */
    public static final String INVALID_INSTITUTION_MESSAGE = "Invalid institutionId";

    public static final Set<String> LIVE_IDS = Set.of(
        "monzo_ob",
        "lloyds_ob",
        "barclays_ob",
        "hsbc_ob",
        "natwest_ob",
        "santander_ob",
        "starling_ob"
    );

    public static boolean isValid(String institutionId) {
        return institutionId != null && LIVE_IDS.contains(institutionId);
    }

    private PayoutInstitutions() {}
}
