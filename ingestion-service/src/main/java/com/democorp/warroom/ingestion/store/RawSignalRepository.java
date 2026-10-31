package com.democorp.warroom.ingestion.store;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RawSignalRepository extends JpaRepository<RawSignal, String> {

    @Query("select r from RawSignal r order by r.receivedAt desc")
    List<RawSignal> latest(Pageable page);
}
