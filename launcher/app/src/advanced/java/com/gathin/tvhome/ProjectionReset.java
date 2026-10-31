package com.gathin.tvhome;

import java.util.concurrent.atomic.AtomicBoolean;

/** Reset both stock corner models, preserving mode/zoom/automatic preferences and exact undo. */
final class ProjectionReset {
    interface Backend {
        void resolve() throws Exception;
        void prepareWrite() throws Exception;
        OrientationPilot.State read() throws Exception;
        int[] driver() throws Exception;
        int coordinates(int[][] points) throws Exception;
        void persist(OrientationPilot.Property key,String value) throws Exception;
        void persistZoom(ProjectionScaleBackend.Key key,String value) throws Exception;
    }
    private final Backend backend;
    private final OrientationPilot.Journal journal;
    private final AtomicBoolean cancelled;
    private OrientationPilot.State expected;
    String stage="idle";
    ProjectionReset(Backend backend,OrientationPilot.Journal journal,AtomicBoolean cancelled){this.backend=backend;this.journal=journal;this.cancelled=cancelled;}
    private void check(){if(cancelled.get())throw new IllegalStateException("Stopped between calls. Original geometry remains in recovery.");}
    private void verify() throws Exception {check();if(!expected.same(backend.read()))throw new IllegalStateException("Projection changed elsewhere; stop");check();}
    private void driver(OrientationPilot.State target) throws Exception {
        int[][] points=OrientationMath.hal(target.display(),1920,1080);int[] actual=backend.driver();
        if(actual==null||actual.length!=8)throw new IllegalStateException("Driver readback unavailable");
        for(int i=0;i<4;i++)if(actual[i*2]!=points[i][0]||actual[i*2+1]!=points[i][1])throw new IllegalStateException("Driver geometry did not match; original recovery retained");
    }
    private OrientationPilot.State stable(boolean recovery) throws Exception {
        check();backend.resolve();check();OrientationPilot.State state=backend.read();
        check();if(!state.same(backend.read()))throw new IllegalStateException("Projection is changing; wait and reopen");
        if("0".equals(state.guard(OrientationPilot.Guard.ZOOM_VALUE))&&ProjectionScaleMath.vector(0).equals(state.guard(OrientationPilot.Guard.VECTOR)))state.validate(!recovery);
        else state.validateScaled(!recovery,!recovery);
        if(!recovery)driver(state);
        check();return state;
    }
    static OrientationPilot.State target(OrientationPilot.State original) {
        original.validateManual(true);OrientationPilot.State target=original;
        for(int i=3;i<19;i++){OrientationPilot.Property p=OrientationPilot.Property.values()[i];if(Integer.parseInt(original.value(p))!=0)target=target.property(p,"0");}
        return target.zoomStep("0").zoomVector(ProjectionScaleMath.vector(0));
    }
    private static void sameOther(OrientationPilot.State a,OrientationPilot.State b) {
        for(int i=3;i<19;i++){OrientationPilot.Property p=OrientationPilot.Property.values()[i];b=b.property(p,a.value(p));}
        b=b.zoomStep(a.guard(OrientationPilot.Guard.ZOOM_VALUE)).zoomVector(a.guard(OrientationPilot.Guard.VECTOR));
        if(!a.same(b))throw new IllegalStateException("Mode, focus or automatic controls changed; stop");
    }
    OrientationPilot.State inspect() throws Exception {
        if(!OrientationPilot.acquireOpticalOperation())throw new IllegalStateException("Another optical operation is running");
        try{return stable(journal.exists());}finally{OrientationPilot.releaseOpticalOperation();}
    }
    void run(OrientationPilot.State reviewed,boolean restore) throws Exception {
        if(!OrientationPilot.acquireOpticalOperation())throw new IllegalStateException("Another optical operation is running");
        boolean attempted=false;
        try{
            if(journal.conflicts())throw new IllegalStateException("Keep or restore the other projection operation first");
            stage="preflight";expected=stable(restore);
            if(!expected.same(reviewed))throw new IllegalStateException("Reviewed geometry is stale. Reopen to review.");
            OrientationPilot.State requested;
            if(restore){if(!journal.exists())throw new IllegalStateException("No reset recovery record");requested=journal.original();requested.validateManual(true);sameOther(requested,expected);}
            else{if(journal.exists())throw new IllegalStateException("Keep or restore the previous reset first");requested=target(expected);if(expected.same(requested))throw new IllegalStateException("Both corner models are already reset");}
            backend.prepareWrite();check();
            if(!restore)journal.create(expected);
            journal.record(restore?"RESTORE_PLAN_TARGET":"APPLY_PLAN_TARGET",requested);
            verify();stage="BEFORE_COORDINATES";journal.record(stage,expected);check();attempted=true;
            if(backend.coordinates(OrientationMath.hal(requested.display(),1920,1080))!=0)throw new IllegalStateException("Coordinate response ambiguous; original recovery retained");
            driver(requested);verify();stage="AFTER_COORDINATES";journal.record(stage,expected);
            for(ProjectionScaleBackend.Key key:ProjectionScaleBackend.Key.values()){
                OrientationPilot.Guard guard=key==ProjectionScaleBackend.Key.STEP?OrientationPilot.Guard.ZOOM_VALUE:OrientationPilot.Guard.VECTOR;
                String value=requested.guard(guard);if(value.equals(expected.guard(guard)))continue;
                verify();stage="BEFORE_"+key.name();journal.record(stage,expected);check();backend.persistZoom(key,value);
                expected=key==ProjectionScaleBackend.Key.STEP?expected.zoomStep(value):expected.zoomVector(value);verify();journal.record("AFTER_"+key.name(),expected);
            }
            OrientationPilot.Property[] order={OrientationPilot.Property.D_LBX,OrientationPilot.Property.D_LTX,OrientationPilot.Property.D_RBX,OrientationPilot.Property.D_RTX,
                OrientationPilot.Property.D_LBY,OrientationPilot.Property.D_LTY,OrientationPilot.Property.D_RBY,OrientationPilot.Property.D_RTY,
                OrientationPilot.Property.H_LTX,OrientationPilot.Property.H_LTY,OrientationPilot.Property.H_LBX,OrientationPilot.Property.H_LBY,
                OrientationPilot.Property.H_RTX,OrientationPilot.Property.H_RTY,OrientationPilot.Property.H_RBX,OrientationPilot.Property.H_RBY};
            for(OrientationPilot.Property key:order){
                if(expected.value(key).equals(requested.value(key)))continue;
                verify();stage="BEFORE_"+key.name();journal.record(stage,expected);check();
                backend.persist(key,requested.value(key));expected=expected.property(key,requested.value(key));
                verify();stage="AFTER_"+key.name();journal.record(stage,expected);
            }
            verify();driver(requested);if(!expected.same(requested))throw new IllegalStateException("Reset target mismatch");
            stage=restore?"RESTORE_STORED_READBACK_OK_PHYSICAL_UNVERIFIED":"APPLY_STORED_READBACK_OK_PHYSICAL_UNVERIFIED";journal.record(stage,expected);
        }catch(Exception failure){stage=(attempted?"UNKNOWN_PARTIAL at ":"STOPPED_BEFORE_WRITE at ")+stage;if(journal.exists())try{journal.record(stage,expected);}catch(Exception logging){failure.addSuppressed(logging);}throw failure;}
        finally{OrientationPilot.releaseOpticalOperation();}
    }
    void complete(OrientationPilot.State reviewed) throws Exception {
        if(!OrientationPilot.acquireOpticalOperation())throw new IllegalStateException("Another optical operation is running");
        try{OrientationPilot.State state=stable(false);if(!state.same(reviewed))throw new IllegalStateException("Geometry changed. Reopen to review.");journal.complete(state);}
        finally{OrientationPilot.releaseOpticalOperation();}
    }
}
