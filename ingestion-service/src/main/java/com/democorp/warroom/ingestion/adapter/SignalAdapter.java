package com.democorp.warroom.ingestion.adapter;

import com.democorp.warroom.common.contracts.SignalPayload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Adapter pattern: turns one tool's payload into the standard {@link SignalPayload}.
 * Add a new source by adding one adapter; nothing downstream changes.
 */
public interface SignalAdapter<T> {

    List<SignalPayload> toSignals(T request);

    static String fingerprint(Object... parts) {
        String joined = Stream.of(parts).map(p -> Objects.toString(p, "")).collect(Collectors.joining("|"));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
