package com.chubb.claims.claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InformationRequestRepository extends JpaRepository<InformationRequest, UUID> {
}
