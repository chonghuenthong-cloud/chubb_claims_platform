package com.chubb.claims.claim;

/** Thrown when a status change breaks the lifecycle rules in {@link ClaimStatus}. */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(ClaimStatus from, ClaimStatus to) {
        super("Cannot move a claim from " + from + " to " + to);
    }
}
