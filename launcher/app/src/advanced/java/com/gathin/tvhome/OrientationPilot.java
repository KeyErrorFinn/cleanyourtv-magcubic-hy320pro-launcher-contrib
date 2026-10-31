package com.gathin.tvhome;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/** One explicit experimental transaction. No startup work, retries, fallback or automatic undo. */
final class OrientationPilot {
    enum Property {
        MODE("persist.sys.panelvalue"), ZOOM_H("persist.display.keystone_zoom_h"), ZOOM_V("persist.display.keystone_zoom_v"),
        D_LTX("persist.display.keystone_ltx"), D_LTY("persist.display.keystone_lty"),
        D_RTX("persist.display.keystone_rtx"), D_RTY("persist.display.keystone_rty"),
        D_LBX("persist.display.keystone_lbx"), D_LBY("persist.display.keystone_lby"),
        D_RBX("persist.display.keystone_rbx"), D_RBY("persist.display.keystone_rby"),
        H_LTX("persist.htc.keystone.ltx"), H_LTY("persist.htc.keystone.lty"),
        H_RTX("persist.htc.keystone.rtx"), H_RTY("persist.htc.keystone.rty"),
        H_LBX("persist.htc.keystone.lbx"), H_LBY("persist.htc.keystone.lby"),
        H_RBX("persist.htc.keystone.rbx"), H_RBY("persist.htc.keystone.rby");
        final String key;
        Property(String key) { this.key = key; }
    }
    // Read-only guards. Neither absent zoom_scale key is written or required.
    enum Guard {
        BUILD("ro.build.id"), SDK("ro.build.version.sdk"), OPTICS("ro.build.characteristics"),
        RASTER("persist.vendor.disp.screensize"), AUTO("persist.sys.tpryauto"), XCRT("persist.sys.tpryxcrt"),
        FOCUS("persist.sys.vafocus"), CALIB("persist.htc.one_touch_calib"), CAM("persist.sys.camok"),
        FOCUS_DIRECTION("persist.sys.focusupdn"), RECOGNIZE("persist.sys.mbrecognize"), OBSTACLE("persist.sys.obstacle"),
        VECTOR("persist.sys.zoom.value"), ZOOM_VALUE("persist.sys.zoom_value");
        final String key;
        Guard(String key) { this.key = key; }
    }
    static final class State {
        private final String[] values, guards;
        final int mirror;
        final MirrorField.Value panel;
        State(String[] values, String[] guards, int mirror) {
            this(values, guards, mirror, null);
        }
        State(String[] values, String[] guards, int mirror, MirrorField.Value panel) {
            this.panel = panel;
            if (values.length != Property.values().length || guards.length != Guard.values().length)
                throw new IllegalArgumentException("State shape");
            this.values = values.clone(); this.guards = guards.clone(); this.mirror = mirror;
        }
        String value(Property p) { return values[p.ordinal()]; }
        String guard(Guard g) { return guards[g.ordinal()]; }
        int mode() { return Integer.parseInt(value(Property.MODE)); }
        String[] display() { return Arrays.copyOfRange(values, 3, 11); }
        String[] htc() { return Arrays.copyOfRange(values, 11, 19); }
        State property(Property p, String value) {
            String[] next = values.clone(); next[p.ordinal()] = value;
            return new State(next, guards, mirror, panel);
        }
        State mirror(int value) { return new State(values, guards, value, panel == null ? null : panel.mode(value)); }
        State zoomStep(String value){ProjectionScaleMath.step(value);String[] next=guards.clone();next[Guard.ZOOM_VALUE.ordinal()]=value;return new State(values,next,mirror,panel);}
        State zoomVector(String value){ProjectionScaleMath.vectorEncoding(value);String[] next=guards.clone();next[Guard.VECTOR.ordinal()]=value;return new State(values,next,mirror,panel);}
        void validateScaled(boolean coherent,boolean paired){
            int index=ProjectionScaleMath.step(guard(Guard.ZOOM_VALUE));ProjectionScaleMath.vectorEncoding(guard(Guard.VECTOR));
            if(paired&&!ProjectionScaleMath.vector(index).equals(guard(Guard.VECTOR)))throw new IllegalArgumentException("Scaling step and vector disagree");
            zoomStep("0").zoomVector(ProjectionScaleMath.vector(0)).validate(coherent);
            if(!"1".equals(guard(Guard.CAM))||!"1".equals(guard(Guard.FOCUS_DIRECTION)))throw new IllegalArgumentException("Direct scaling requires the reviewed no-camera branch");
        }
        void validateManual(boolean coherent){
            if("0".equals(guard(Guard.ZOOM_VALUE))&&ProjectionScaleMath.vector(0).equals(guard(Guard.VECTOR)))validate(coherent);
            else validateScaled(coherent,true);
        }
        boolean same(State other) {
            return other != null && mirror == other.mirror && Arrays.equals(values, other.values)
                    && Arrays.equals(guards, other.guards)
                    && (panel == null ? other.panel == null : panel.same(other.panel));
        }
        void validate(boolean coherent) {
            for (Property p : Property.values()) {
                String v = value(p);
                if (v == null || v.isEmpty()) throw new IllegalArgumentException("Required property absent: " + p.name());
                if (p != Property.ZOOM_H && p != Property.ZOOM_V) Integer.parseInt(v);
            }
            for (String guard : guards) if (guard == null || guard.isEmpty())
                throw new IllegalArgumentException("Required guard absent");
            if (!"Projector_20260713.153921".equals(guard(Guard.BUILD)) || !"34".equals(guard(Guard.SDK))
                    || !"optics".equals(guard(Guard.OPTICS)) || !"1920x1080".equals(guard(Guard.RASTER)))
                throw new IllegalArgumentException("Unreviewed firmware/optics/raster");
            for (Guard g : new Guard[] {Guard.AUTO, Guard.XCRT, Guard.RECOGNIZE, Guard.OBSTACLE})
                if (!"0".equals(guard(g))) throw new IllegalArgumentException("Automatic correction/recognition unsupported");
            if (!"0,0,0,0,0,0,0,0".equals(guard(Guard.VECTOR)) || !"0".equals(guard(Guard.ZOOM_VALUE))) {
                int step=ProjectionScaleMath.step(guard(Guard.ZOOM_VALUE));
                if(!ProjectionScaleMath.vector(step).equals(guard(Guard.VECTOR))||!"1".equals(guard(Guard.CAM))||!"1".equals(guard(Guard.FOCUS_DIRECTION)))
                    throw new IllegalArgumentException("Unreviewed scaling/vector state");
            }
            for (Guard g : new Guard[] {Guard.FOCUS, Guard.CAM, Guard.FOCUS_DIRECTION})
                if (!"0".equals(guard(g)) && !"1".equals(guard(g)))
                    throw new IllegalArgumentException("Unknown focus/camera guard encoding");
            if (!"true".equals(guard(Guard.CALIB)) && !"false".equals(guard(Guard.CALIB)))
                throw new IllegalArgumentException("Unknown calibration guard encoding");
            OrientationMath.mode(mode()); OrientationMath.zoom(value(Property.ZOOM_H), value(Property.ZOOM_V));
            if (mirror < 0 || mirror > 3 || (coherent && mirror != OrientationMath.nativeBits(mode())))
                throw new IllegalArgumentException("Native/logical mode mismatch");
            if (panel == null || panel.path != MirrorField.Path.OEM || panel.stored != mirror)
                throw new IllegalArgumentException("Present reviewed native panel field required");
            if (coherent) OrientationMath.hal(display(), 1920, 1080);
        }
        State target(int mode) {
            OrientationMath.mode(mode);
            State next = property(Property.MODE, Integer.toString(mode)).mirror(OrientationMath.nativeBits(mode));
            String[] display = OrientationMath.permute(display(), mode(), mode);
            String[] htc = OrientationMath.permute(htc(), mode(), mode);
            for (int i = 0; i < 8; i++) {
                next = next.property(Property.values()[3 + i], display[i]);
                next = next.property(Property.values()[11 + i], htc[i]);
            }
            return next.property(Property.ZOOM_H, "100.0").property(Property.ZOOM_V, "100.0");
        }
    }
    interface Backend {
        void resolve() throws Exception;
        void prepareWrite() throws Exception;
        State read() throws Exception;
        int flip(boolean horizontal, boolean vertical) throws Exception;
        MirrorReceipt mirror(int logicalMode) throws Exception;
        int coordinates(int[][] points) throws Exception;
        void persist(Property property, String encoding) throws Exception;
    }
    interface Journal {
        boolean exists();
        default boolean conflicts() { return false; }
        State original() throws Exception;
        void create(State original) throws Exception;
        void record(String stage, State expected) throws Exception;
        default void verifyApplied(State confirmed) throws Exception { throw new IllegalStateException("Resume verification unsupported"); }
        default void complete(State confirmed) throws Exception { throw new IllegalStateException("Completion unsupported"); }
    }
    // Shared across pages/Activity recreation; held until even an uncancellable call returns.
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    static boolean busy() { return BUSY.get(); }
    static boolean acquireOpticalOperation() { return BUSY.compareAndSet(false, true); }
    static void releaseOpticalOperation() { BUSY.set(false); }
    private final Backend backend;
    private final Journal journal;
    private final AtomicBoolean cancelled;
    private State expected;
    private boolean mutationAttempted;
    volatile String stage = "idle";
    OrientationPilot(Backend backend, Journal journal, AtomicBoolean cancelled) {
        this.backend = backend; this.journal = journal; this.cancelled = cancelled;
    }
    private void check() throws Exception {
        if (cancelled.get()) throw new IllegalStateException("Stopped between calls; no automatic undo");
    }
    State inspect() throws Exception {
        if (!BUSY.compareAndSet(false, true)) throw new IllegalStateException("Another operation is still running");
        try { return stable(true); } finally { BUSY.set(false); }
    }
    private State stable(boolean coherent) throws Exception {
        check(); stage = "resolve normal APIs"; backend.resolve(); check();
        stage = "stable stored-state preflight";
        State first = backend.read(); check(); State second = backend.read(); check();
        if (!first.same(second)) throw new IllegalStateException("Concurrent state change");
        first.validate(coherent); return first;
    }
    /** Selection's captured state must still match; Restore requires an explicitly freshly reviewed capture. */
    void run(State reviewed, int selectedMode, boolean restore) throws Exception {
        if (!BUSY.compareAndSet(false, true)) throw new IllegalStateException("Another operation is still running");
        try {
            mutationAttempted = false;
            expected = null;
            if (journal.conflicts()) throw new IllegalStateException("Manual-corner recovery is unresolved; orientation writes blocked");
            expected = stable(!restore);
            if (!expected.same(reviewed)) throw new IllegalStateException("Selection is stale; read again");
            stage = "native write-result contract gate";
            backend.prepareWrite(); // Readiness and independent live-writer switch both precede flip.
            State target;
            if (restore) {
                if (!journal.exists()) throw new IllegalStateException("No original backup");
                target = journal.original(); target.validate(true);
                if (!Arrays.equals(expected.guards, target.guards)) throw new IllegalStateException("Original guards changed");
                if (!expected.panel.mode(target.mirror).same(target.panel))
                    throw new IllegalStateException("Original panel path/surroundings changed");
            } else {
                OrientationMath.mode(selectedMode);
                if (journal.exists()) throw new IllegalStateException("Existing journal: restore or confirm the completed picture first");
                if (selectedMode == expected.mode()) throw new IllegalArgumentException("Same-mode apply is not a no-op probe");
                target = expected.target(selectedMode); target.validate(true);
                journal.create(expected); // Durable immutable original BEFORE the first mutation.
            }
            journal.record(restore ? "RESTORE_PLAN_TARGET" : "APPLY_PLAN_TARGET", target);
            checkpoint(restore ? "RESTORE_BEGIN" : "APPLY_BEGIN");
            before("FLIP"); result(backend.flip(OrientationMath.horizontal(target.mode()), OrientationMath.vertical(target.mode())));
            after("FLIP");
            before("MIRROR");
            MirrorReceipt receipt = backend.mirror(target.mode());
            if (receipt == null) throw new IllegalStateException("Missing typed mirror receipt");
            // Persist the raw response before interpreting it; a journal failure stops subsequent writes.
            checkpoint("MIRROR_RAW_" + receipt.rawCode);
            receipt.verify(target.mode(), expected.panel);
            expected = expected.mirror(target.mirror); after("MIRROR");
            checkpoint("MIRROR_" + MirrorReceipt.VERIFIED);
            write(Property.MODE, target, restore);
            before("COORDINATES"); result(backend.coordinates(OrientationMath.hal(target.display(), 1920, 1080)));
            after("COORDINATES");
            // Exact stock x2.d.c persistence order, independent of HAL LT/RT/LB/RB order.
            Property[] displayOrder = {Property.ZOOM_H, Property.ZOOM_V, Property.D_LBX, Property.D_LTX,
                    Property.D_RBX, Property.D_RTX, Property.D_LBY, Property.D_LTY, Property.D_RBY, Property.D_RTY};
            for (Property p : displayOrder) write(p, target, restore);
            // Raw x() 0262..0297: LT, LB, RT, RB (each XY intact).
            Property[] htcOrder = {Property.H_LTX, Property.H_LTY, Property.H_LBX, Property.H_LBY,
                    Property.H_RTX, Property.H_RTY, Property.H_RBX, Property.H_RBY};
            for (Property p : htcOrder) write(p, target, restore);
            verify();
            if (!expected.same(target)) throw new IllegalStateException("Final requested state mismatch");
            checkpoint(restore ? "RESTORE_STORED_READBACK_OK_PHYSICAL_UNVERIFIED" : "APPLY_STORED_READBACK_OK_PHYSICAL_UNVERIFIED");
        } catch (Exception failure) {
            String failedStage = stage;
            stage = (mutationAttempted ? "UNKNOWN_PARTIAL at " : "STOPPED_BEFORE_WRITE at ") + failedStage;
            if (journal.exists()) {
                try { journal.record(stage + ": " + failure.getClass().getSimpleName(), expected); }
                catch (Exception logFailure) { failure.addSuppressed(logFailure); }
            }
            throw failure;
        } finally { BUSY.set(false); }
    }
    State inspectRestore() throws Exception {
        if (!BUSY.compareAndSet(false, true)) throw new IllegalStateException("Another operation is still running");
        try { return stable(false); } finally { BUSY.set(false); }
    }
    /** Explicit owner acknowledgement only; no optical writes or automatic completion. */
    void complete(State reviewed) throws Exception {
        if (!BUSY.compareAndSet(false, true)) throw new IllegalStateException("Another operation is still running");
        try {
            State current = stable(true);
            if (!current.same(reviewed)) throw new IllegalStateException("Confirmation is stale; read again");
            journal.complete(current);
        } finally { BUSY.set(false); }
    }
    private void checkpoint(String label) throws Exception { stage = label; journal.record(label, expected); }
    private void verify() throws Exception {
        check(); if (!expected.same(backend.read())) throw new IllegalStateException("Unexpected state/readback; stop"); check();
    }
    private void before(String label) throws Exception {
        stage = label; verify(); checkpoint("BEFORE_" + label); check(); mutationAttempted = true;
    }
    private void after(String label) throws Exception { verify(); checkpoint("AFTER_" + label); }
    private static void result(int code) {
        if (code != 0) throw new IllegalStateException("Vendor returned " + code + "; outcome unverified");
    }
    private void write(Property property, State target, boolean restore) throws Exception {
        if (restore && expected.value(property).equals(target.value(property))) {
            // A denied Apply may have changed hardware but no properties. Do not require
            // another denied no-op write before restoring the original coordinates.
            verify(); checkpoint("RESTORE_UNCHANGED_" + property.name()); return;
        }
        before(property.name()); backend.persist(property, target.value(property));
        expected = expected.property(property, target.value(property)); after(property.name());
    }
}
