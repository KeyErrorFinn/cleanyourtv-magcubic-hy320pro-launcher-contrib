package com.gathin.tvhome;

import java.util.Objects;

/** Short-lived correlation only. Actual attachment still requires the Android shell UID. */
final class StarterHandshake {
    private String pending;
    private String accepted;
    private long deadline;
    private boolean pendingIndependent;

    synchronized String begin(String fresh, long now) { return begin(fresh, now, false); }

    synchronized String begin(String fresh, long now, boolean independent) {
        if (!valid(fresh)) throw new IllegalArgumentException("Invalid starter token");
        if (pending != null && now < deadline)
            throw new IllegalStateException("A helper start is already in progress");
        pending = fresh;
        pendingIndependent = independent;
        accepted = null;
        deadline = now + 60_000;
        return pending;
    }

    synchronized boolean attached(String token, long now) {
        accepted = valid(token) && now < deadline && Objects.equals(pending, token)
                ? token : null;
        pending = null;
        boolean independent = accepted != null && pendingIndependent;
        pendingIndependent = false;
        return independent;
    }

    synchronized boolean matches(String token) {
        return valid(token) && Objects.equals(accepted, token);
    }

    static boolean valid(String value) {
        return value != null && value.matches("[0-9a-f]{32}");
    }
}
