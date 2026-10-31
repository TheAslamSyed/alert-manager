package com.democorp.warroom.ingestion.store;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SignalFingerprintRepository extends JpaRepository<SignalFingerprint, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from SignalFingerprint f where f.fingerprint = ?1")
    Optional<SignalFingerprint> findForUpdate(String fingerprint);
}
