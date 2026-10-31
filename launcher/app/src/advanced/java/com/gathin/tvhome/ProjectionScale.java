package com.gathin.tvhome;

import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit stock-branch scaling with immutable originals, driver readback and partial recovery. */
final class ProjectionScale {
    interface Backend extends ProjectionReset.Backend { }
    private final Backend backend;private final OrientationPilot.Journal journal;private final AtomicBoolean cancelled;
    private OrientationPilot.State expected;String stage="idle";
    ProjectionScale(Backend backend,OrientationPilot.Journal journal,AtomicBoolean cancelled){this.backend=backend;this.journal=journal;this.cancelled=cancelled;}
    private void check(){if(cancelled.get())throw new IllegalStateException("Stopped between calls; previous geometry is retained for Restore");}
    private void driver(OrientationPilot.State state)throws Exception{
        int[] actual=backend.driver();int[][] points=OrientationMath.hal(state.display(),1920,1080);
        if(actual==null||actual.length!=8)throw new IllegalStateException("Driver readback unavailable");
        for(int i=0;i<4;i++)if(actual[2*i]!=points[i][0]||actual[2*i+1]!=points[i][1])throw new IllegalStateException("Driver geometry mismatch; Restore record retained");
    }
    private OrientationPilot.State stable(boolean recovery)throws Exception{
        check();backend.resolve();check();OrientationPilot.State state=backend.read();check();
        if(!state.same(backend.read()))throw new IllegalStateException("Projection is changing; reopen when stable");
        state.validateScaled(!recovery,!recovery);if(!recovery)driver(state);check();return state;
    }
    private void verify()throws Exception{check();if(!expected.same(backend.read()))throw new IllegalStateException("Projection changed elsewhere; stop");check();}
    static void sameOther(OrientationPilot.State original,OrientationPilot.State current){
        for(int i=3;i<11;i++){OrientationPilot.Property key=OrientationPilot.Property.values()[i];current=current.property(key,original.value(key));}
        current=current.zoomStep(original.guard(OrientationPilot.Guard.ZOOM_VALUE)).zoomVector(original.guard(OrientationPilot.Guard.VECTOR));
        if(!original.same(current))throw new IllegalStateException("Mode, focus, saved base or automatic controls changed; stop");
    }
    OrientationPilot.State inspect()throws Exception{
        if(!OrientationPilot.acquireOpticalOperation())throw new IllegalStateException("Another optical operation is running");
        try{return stable(journal.exists());}finally{OrientationPilot.releaseOpticalOperation();}
    }
    void run(OrientationPilot.State reviewed,int step,boolean restore)throws Exception{
        if(!OrientationPilot.acquireOpticalOperation())throw new IllegalStateException("Another optical operation is running");boolean attempted=false;
        try{
            if(journal.conflicts())throw new IllegalStateException("Keep or restore the other projection change first");
            stage="preflight";expected=stable(restore);if(!expected.same(reviewed))throw new IllegalStateException("Scaling selection is stale; refresh before applying");
            OrientationPilot.State target;
            if(restore){if(!journal.exists())throw new IllegalStateException("No scaling Restore record");target=journal.original();target.validateScaled(true,true);sameOther(target,expected);}
            else{if(journal.exists())throw new IllegalStateException("Keep or restore the previous scaling change first");if(step==ProjectionScaleMath.step(expected.guard(OrientationPilot.Guard.ZOOM_VALUE)))throw new IllegalStateException("Scaling step is unchanged. Your manual corners were retained.");target=ProjectionScaleMath.target(expected,step);}
            backend.prepareWrite();check();if(!restore)journal.create(expected);
            journal.record(restore?"RESTORE_PLAN_TARGET":"APPLY_PLAN_TARGET",target);verify();stage="BEFORE_COORDINATES";journal.record(stage,expected);check();attempted=true;
            if(backend.coordinates(OrientationMath.hal(target.display(),1920,1080))!=0)throw new IllegalStateException("Ambiguous coordinate response");
            driver(target);verify();journal.record("AFTER_COORDINATES",expected);
            for(ProjectionScaleBackend.Key key:ProjectionScaleBackend.Key.values()){
                OrientationPilot.Guard guard=key==ProjectionScaleBackend.Key.STEP?OrientationPilot.Guard.ZOOM_VALUE:OrientationPilot.Guard.VECTOR;
                String value=target.guard(guard);if(value.equals(expected.guard(guard)))continue;
                verify();stage="BEFORE_"+key.name();journal.record(stage,expected);check();backend.persistZoom(key,value);
                expected=key==ProjectionScaleBackend.Key.STEP?expected.zoomStep(value):expected.zoomVector(value);verify();journal.record("AFTER_"+key.name(),expected);
            }
            for(OrientationPilot.Property key:new OrientationPilot.Property[]{OrientationPilot.Property.D_LBX,OrientationPilot.Property.D_LTX,OrientationPilot.Property.D_RBX,OrientationPilot.Property.D_RTX,OrientationPilot.Property.D_LBY,OrientationPilot.Property.D_LTY,OrientationPilot.Property.D_RBY,OrientationPilot.Property.D_RTY}){
                String value=target.value(key);if(value.equals(expected.value(key)))continue;
                verify();stage="BEFORE_"+key.name();journal.record(stage,expected);check();backend.persist(key,value);expected=expected.property(key,value);verify();journal.record("AFTER_"+key.name(),expected);
            }
            verify();driver(target);if(!expected.same(target))throw new IllegalStateException("Scaling target mismatch");
            stage=restore?"RESTORE_STORED_READBACK_OK_PHYSICAL_UNVERIFIED":"APPLY_STORED_READBACK_OK_PHYSICAL_UNVERIFIED";journal.record(stage,expected);
        }catch(Exception failure){stage=(attempted?"UNKNOWN_PARTIAL at ":"STOPPED_BEFORE_WRITE at ")+stage;if(journal.exists())try{journal.record(stage,expected);}catch(Exception logging){failure.addSuppressed(logging);}throw failure;}
        finally{OrientationPilot.releaseOpticalOperation();}
    }
    void complete(OrientationPilot.State reviewed)throws Exception{
        if(!OrientationPilot.acquireOpticalOperation())throw new IllegalStateException("Another optical operation is running");
        try{OrientationPilot.State state=stable(false);if(!state.same(reviewed))throw new IllegalStateException("Projection changed; refresh before keeping");journal.complete(state);}finally{OrientationPilot.releaseOpticalOperation();}
    }
}
