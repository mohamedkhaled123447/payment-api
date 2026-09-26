package com.payverse.paymentapi.threeds.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ThreeDSSessionRepository extends JpaRepository<ThreeDSSession, UUID> {
}
