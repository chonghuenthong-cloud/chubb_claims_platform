package com.chubb.claims.claim;

/**
 * Thrown when an action doesn't fit the claim's current state (e.g. approving a claim that isn't in review,
 * or picking up a claim someone already took). Returned as 409 Conflict.
 */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(ClaimStatus from, ClaimStatus to) {
        super("Cannot move a claim from " + from + " to " + to);
    }

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
