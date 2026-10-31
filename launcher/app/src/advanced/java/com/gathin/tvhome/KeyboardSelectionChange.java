package com.gathin.tvhome;

import java.util.Objects;

/** Two-key selection with durable originals, stale checks and partial-failure recovery. */
final class KeyboardSelectionChange {
    interface Backend {
        String id(); String subtype();
        boolean id(String value); boolean subtype(String value);
        void capture(String id, String subtype);
    }
    static void apply(Backend b, String reviewed, String subtype, String target) {
        if (!Objects.equals(reviewed, b.id()) || !Objects.equals(subtype, b.subtype()))
            throw new IllegalStateException("Keyboard changed. Reopen and try again.");
        if (target.equals(reviewed)) return;
        b.capture(reviewed, subtype);
        try {
            if (!b.subtype("-1") || !"-1".equals(b.subtype())) throw new IllegalStateException("Subtype was not confirmed");
            if (!Objects.equals(reviewed, b.id())) throw new IllegalStateException("Keyboard changed concurrently");
            if (!b.id(target) || !target.equals(b.id()) || !"-1".equals(b.subtype()))
                throw new IllegalStateException("Selection was not confirmed");
        } catch (RuntimeException failure) {
            String now = b.id(), nowSubtype = b.subtype();
            if ((!Objects.equals(now, reviewed) && !target.equals(now))
                    || (!Objects.equals(nowSubtype, subtype) && !"-1".equals(nowSubtype)))
                throw new IllegalStateException("Keyboard changed concurrently. Originals are saved; reopen to inspect.", failure);
            boolean restored = b.id(reviewed) && b.subtype(subtype)
                    && Objects.equals(reviewed, b.id()) && Objects.equals(subtype, b.subtype());
            throw new IllegalStateException(restored ? "Selection failed; original keyboard restored."
                    : "Selection failed. Original values are saved; restoration needs attention.", failure);
        }
    }
    private KeyboardSelectionChange() { }
}
