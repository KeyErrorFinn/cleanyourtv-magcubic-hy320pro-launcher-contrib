package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class StarterHandshakeTest {
    private static final String A = "0123456789abcdef0123456789abcdef";
    private static final String B = "fedcba9876543210fedcba9876543210";

    @Test public void onlyFreshMatchingAttachmentProvesTheStart() {
        StarterHandshake h = new StarterHandshake();
        assertEquals(A, h.begin(A, 100));
        assertFalse(h.matches(A));
        h.attached(A, 101);
        assertTrue(h.matches(A));
        assertFalse(h.matches(B));
    }

    @Test public void rejectsOverlappingStartsUntilTheLeaseExpires() {
        StarterHandshake h = new StarterHandshake();
        h.begin(A, 100);
        try { h.begin(B, 101); fail(); } catch (IllegalStateException expected) { }
        assertEquals(B, h.begin(B, 60_100));
        h.attached(A, 60_101);
        assertFalse(h.matches(A));
    }

    @Test public void expiredUncorrelatedAndMissingTokensDoNotProveAttachment() {
        StarterHandshake h = new StarterHandshake();
        h.begin(A, 100);
        h.attached(A, 60_100);
        assertFalse(h.matches(A));
        h.attached(B, 60_101);
        assertFalse(h.matches(B));
        h.attached(null, 60_102);
        assertFalse(h.matches(null));
    }

    @Test public void newStartInvalidatesPreviousProof() {
        StarterHandshake h = new StarterHandshake();
        h.begin(A, 0);
        h.attached(A, 1);
        h.begin(B, 2);
        assertFalse(h.matches(A));
    }

    @Test public void onlyMatchingSignedIndependentRequestCanSurviveDebuggingOff() {
        StarterHandshake h = new StarterHandshake();
        h.begin(A, 10, true);
        assertFalse(h.attached(B, 11));
        h.begin(A, 12, false);
        assertFalse(h.attached(A, 13));
        h.begin(A, 14, true);
        assertTrue(h.attached(A, 15));
        assertFalse(h.attached(A, 16));
        h.begin(A, 20, true);
        assertFalse(h.attached(A, 60_020));
    }

    @Test public void tokenCannotCarryShellSyntax() {
        for (String bad : new String[]{null, "", "../path", "$(id)", A + "'", A.toUpperCase()})
            assertFalse(StarterHandshake.valid(bad));
        try { new StarterHandshake().begin(";id", 0); fail(); }
        catch (IllegalArgumentException expected) { }
    }
}
