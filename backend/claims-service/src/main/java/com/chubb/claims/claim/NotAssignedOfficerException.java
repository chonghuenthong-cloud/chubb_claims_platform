package com.chubb.claims.claim;

/** Thrown when a staff member acts on a claim that isn't assigned to them. Returned as 403 Forbidden. */
public class NotAssignedOfficerException extends RuntimeException {

    public NotAssignedOfficerException() {
        super("Only the assigned officer can do this");
    }
}
