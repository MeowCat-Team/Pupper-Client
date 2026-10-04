package cn.pupperclient.platform;

import java.util.Objects;

/** A protocol observation, including whether a real in-client translator is available. */
public record ProtocolSnapshot(String name, int number, boolean version1_8, boolean translationAvailable) {
    public ProtocolSnapshot {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("Protocol name is blank");
        if (version1_8 && !translationAvailable) {
            throw new IllegalArgumentException("Legacy animation compatibility requires an actual translator");
        }
    }
}
