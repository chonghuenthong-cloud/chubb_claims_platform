package com.chubb.claims.claim;

import java.math.BigDecimal;

/** Thrown when a reserve or approved amount is above the policy's coverage limit. Returned as 400 on the "amount" field. */
public class AmountAboveCoverageLimitException extends RuntimeException {

    public AmountAboveCoverageLimitException(BigDecimal limit, String currency) {
        super("Amount cannot exceed the coverage limit of " + currency + " " + limit.toPlainString());
    }
}
