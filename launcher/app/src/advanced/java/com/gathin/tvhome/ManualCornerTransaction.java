package com.gathin.tvhome;

import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit bounded manual correction with durable original and separate Keep/Restore. */
final class ManualCornerTransaction {
    interface Backend {
        void resolve() throws Exception;
        void prepareWrite() throws Exception;
        OrientationPilot.State read() throws Exception;
        int coordinates(int[][] points) throws Exception;
        void persist(OrientationPilot.Property property, String encoding) throws Exception;
    }
    private final Backend backend;
    private final OrientationPilot.Journal journal;
    private final AtomicBoolean cancelled;
    private OrientationPilot.State expected;
    private OrientationPilot.State liveExpected;
    String stage = "idle";
    ManualCornerTransaction(Backend backend, OrientationPilot.Journal journal, AtomicBoolean cancelled) {
        this.backend = backend; this.journal = journal; this.cancelled = cancelled;
    }
    private void check() {
        if (cancelled.get()) throw new IllegalStateException("Cancelled between calls; no automatic undo");
    }
    private OrientationPilot.State stable() throws Exception {
        check(); backend.resolve(); check();
        OrientationPilot.State first = backend.read(); check();
        if (!first.same(backend.read())) throw new IllegalStateException("Concurrent geometry change");
        check(); first.validateManual(false);
        if (first.mode() != 0) throw new IllegalArgumentException("Front mode required");
        return first;
    }
    OrientationPilot.State inspect(boolean restore) throws Exception {
        if (!OrientationPilot.acquireOpticalOperation()) throw new IllegalStateException("Another optical operation is running");
        try {
            OrientationPilot.State current = stable();
            if (!restore) new ManualCornerModel(current,1000,1000);
            return current;
        } finally { OrientationPilot.releaseOpticalOperation(); }
    }
    void resume(OrientationPilot.State reviewed) throws Exception {
        if (!OrientationPilot.acquireOpticalOperation()) throw new IllegalStateException("Another optical operation is running");
        try {
            liveExpected=null;
            if(journal.conflicts()||!journal.exists())throw new IllegalStateException("No resumable manual session");
            OrientationPilot.State current=stable();new ManualCornerModel(current,1000,1000);
            if(!current.same(reviewed))throw new IllegalStateException("Resume state changed");
            journal.verifyApplied(current);
            liveExpected=current;
        } finally {OrientationPilot.releaseOpticalOperation();}
    }
    void complete(OrientationPilot.State reviewed) throws Exception {
        if (!OrientationPilot.acquireOpticalOperation()) throw new IllegalStateException("Another optical operation is running");
        try {
            OrientationPilot.State current = stable(); new ManualCornerModel(current,1000,1000);
            if (!current.same(reviewed)) throw new IllegalStateException("Stale physical confirmation; read again");
            journal.complete(current);
        } finally { OrientationPilot.releaseOpticalOperation(); }
    }
    private static void sameNonDisplay(OrientationPilot.State before, OrientationPilot.State after) {
        OrientationPilot.State normalized = after;
        for (int i = 3; i < 11; i++) {
            OrientationPilot.Property p = OrientationPilot.Property.values()[i];
            normalized = normalized.property(p, before.value(p));
        }
        if (!before.same(normalized)) throw new IllegalArgumentException("Orientation, zoom, camera or guard changed");
    }
    static void validateChange(OrientationPilot.State before, OrientationPilot.State target) {
        new ManualCornerModel(before, 1000, 1000); new ManualCornerModel(target, 1000, 1000);
        sameNonDisplay(before, target); int changes = 0;
        for (int i = 3; i < 11; i++) {
            OrientationPilot.Property p = OrientationPilot.Property.values()[i];
            if (!before.value(p).equals(target.value(p))) {
                if (Integer.parseInt(before.value(p)) == Integer.parseInt(target.value(p)))
                    throw new IllegalArgumentException("Encoding-only changes are not geometry changes");
                changes++;
            }
        }
        if (changes == 0) throw new IllegalArgumentException("No geometry change to apply");
    }
    private void record(String label) throws Exception { stage = label; journal.record(label, expected); }
    private void verify() throws Exception {
        check(); if (!expected.same(backend.read())) throw new IllegalStateException("Unexpected readback; stop"); check();
    }
    void run(OrientationPilot.State reviewed, OrientationPilot.State requested, boolean restore) throws Exception {
        runInternal(reviewed, requested, restore, false);
    }
    /** Only this in-memory session may extend its last fully verified write. Original stays immutable. */
    void live(OrientationPilot.State reviewed, OrientationPilot.State requested) throws Exception {
        runInternal(reviewed, requested, false, true);
    }
    private void runInternal(OrientationPilot.State reviewed, OrientationPilot.State requested, boolean restore, boolean live) throws Exception {
        if (!OrientationPilot.acquireOpticalOperation()) throw new IllegalStateException("Another optical operation is running");
        boolean attempted = false;
        try {
            if (journal.conflicts()) throw new IllegalStateException("Orientation recovery is unresolved; manual writes blocked");
            stage = "stable preflight"; expected = stable();
            if (!expected.same(reviewed)) throw new IllegalStateException("Stale reviewed geometry");
            OrientationPilot.State target;
            if (restore) {
                if (!journal.exists()) throw new IllegalStateException("No original recovery record");
                target = journal.original(); new ManualCornerModel(target,1000,1000);
                sameNonDisplay(target, expected);
            } else {
                if (journal.exists() && !(live && liveExpected != null && liveExpected.same(expected)))
                    throw new IllegalStateException("Resolve the existing operation first");
                validateChange(expected, requested); target = requested;
            }
            stage = "manual writer gate"; backend.prepareWrite(); check();
            if (!restore && !journal.exists()) journal.create(expected);
            liveExpected = null;
            journal.record(restore ? "RESTORE_PLAN_TARGET" : "APPLY_PLAN_TARGET", target);
            verify(); record("BEFORE_COORDINATES"); check(); attempted = true;
            if (backend.coordinates(OrientationMath.hal(target.display(),1920,1080)) != 0)
                throw new IllegalStateException("Coordinate result ambiguous");
            verify(); record("AFTER_COORDINATES");
            OrientationPilot.Property[] order = {OrientationPilot.Property.D_LBX, OrientationPilot.Property.D_LTX,
                    OrientationPilot.Property.D_RBX, OrientationPilot.Property.D_RTX, OrientationPilot.Property.D_LBY,
                    OrientationPilot.Property.D_LTY, OrientationPilot.Property.D_RBY, OrientationPilot.Property.D_RTY};
            for (OrientationPilot.Property p : order) {
                if (expected.value(p).equals(target.value(p))) continue;
                verify(); record("BEFORE_" + p.name()); check();
                backend.persist(p,target.value(p)); expected = expected.property(p,target.value(p));
                verify(); record("AFTER_" + p.name());
            }
            verify(); if (!expected.same(target)) throw new IllegalStateException("Target mismatch");
            record(restore ? "RESTORE_STORED_READBACK_OK_PHYSICAL_UNVERIFIED" : "APPLY_STORED_READBACK_OK_PHYSICAL_UNVERIFIED");
            if (live) liveExpected = expected;
        } catch (Exception failure) {
            liveExpected = null;
            stage = (attempted ? "UNKNOWN_PARTIAL at " : "STOPPED_BEFORE_WRITE at ") + stage;
            if (journal.exists()) try { journal.record(stage,expected); } catch (Exception logging) { failure.addSuppressed(logging); }
            throw failure;
        } finally { OrientationPilot.releaseOpticalOperation(); }
    }
}
