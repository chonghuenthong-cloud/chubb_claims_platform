package com.chubb.claims.claim;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    /** A claimant's own claims, newest first. Loads what the list shows in one query. */
    @EntityGraph(attributePaths = {"policy", "claimant", "assignedOfficer"})
    List<Claim> findByClaimantIdOrderBySubmittedAtDesc(UUID claimantId);

    /** Every claim, newest first, for the staff work queue. */
    @EntityGraph(attributePaths = {"policy", "claimant", "assignedOfficer"})
    List<Claim> findAllByOrderBySubmittedAtDesc();

    /** One claim with everything the detail view shows, including history and who made each change. */
    @EntityGraph(attributePaths = {"policy", "claimant", "assignedOfficer", "history", "history.changedBy"})
    Optional<Claim> findDetailedById(UUID id);

    /** Next value for the human-readable claim number (CLM-000042). */
    @Query(value = "SELECT nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimNumber();
}
