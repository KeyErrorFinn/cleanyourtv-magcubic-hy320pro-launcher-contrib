package com.gathin.tvhome;

import android.content.Context;
import java.util.HashMap;

/** Settings bookkeeping only: archive verified finished work; never replay geometry or calibrate. */
final class ProjectionWorkflows {
    private static final HashMap<String,Long> quietSince=new HashMap<>();
    private static final HashMap<String,AutomaticProjectionControl.Snapshot> quietState=new HashMap<>();
    static boolean settled(AutomaticProjectionControl.Snapshot a,AutomaticProjectionControl.Snapshot b){return a.noRequest()&&b.noRequest()&&a.same(b);}
    private static void driver(OrientationPilot.State state)throws Exception{
        if(!DriverKeystoneState.matches(state,DriverKeystoneState.read()))throw new IllegalStateException("The picture is still changing. Try again shortly.");
    }
    static void prepare(Context context,OrientationJournal.Scope current)throws Exception{
        prepareOnce(context,current);
    }
    static void prepare(Context context,OrientationJournal.Scope current,java.util.concurrent.atomic.AtomicBoolean cancelled)throws Exception{
        ProjectionReadiness.await(()->prepareOnce(context,current),cancelled,android.os.SystemClock::elapsedRealtime,Thread::sleep);
    }
    private static void prepareOnce(Context context,OrientationJournal.Scope current)throws Exception{
        if(!OrientationPilot.acquireOpticalOperation())throw new ProjectionReadiness.Pending("An adjustment is still running.");
        try{
            if(current!=OrientationJournal.Scope.MANUAL_CORNERS&&context.getSharedPreferences("alignment_bounds_preview",0).getBoolean("pending",false))throw new IllegalStateException("Restore the full-light preview first.");
            AutomaticProjectionJournal automatic=new AutomaticProjectionJournal(context.getFilesDir());
            if(automatic.exists()){
                AutomaticProjectionBackend backend=new AutomaticProjectionBackend(context);AutomaticProjectionControl.Snapshot a=backend.read();
                String key=context.getFilesDir().getAbsolutePath();long elapsed=android.os.SystemClock.elapsedRealtime();
                if(!a.noRequest()){quietSince.remove(key);quietState.remove(key);throw new ProjectionReadiness.Pending("The projector is still adjusting.");}
                AutomaticProjectionControl.Snapshot prior=quietState.get(key);
                if(prior==null||!prior.same(a)){quietState.put(key,a);quietSince.put(key,elapsed);}
                boolean observed=elapsed-quietSince.get(key)>=35000L;
                if(!automatic.maySettle(System.currentTimeMillis())&&!observed)throw new ProjectionReadiness.Pending("The projector is finishing its adjustment.");
                try{driver(a.geometry);}catch(IllegalStateException changing){throw new ProjectionReadiness.Pending("The picture is still changing.");}
                Thread.sleep(400);AutomaticProjectionControl.Snapshot b=backend.read();
                if(!settled(a,b))throw new ProjectionReadiness.Pending("The picture is still changing.");
                try{driver(b.geometry);}catch(IllegalStateException changing){throw new ProjectionReadiness.Pending("The picture is still changing.");}automatic.settled();quietSince.remove(key);quietState.remove(key);
            }
            // OEM focus can also be requested outside this launcher: wait on its live flags.
            for(String flag:new String[]{"hotack.sensor.anti_shake","hotack.vaf.runEnable","hotack.vaf.runend"}){
                String value=ProjectionScaleBackend.property(flag,"");
                if("1".equals(value))throw new ProjectionReadiness.Pending("The projector is still adjusting.");
                if(!"0".equals(value))throw new IllegalStateException("Unknown manufacturer activity status");
            }
            OrientationVendorBackend reader=null;
            for(OrientationJournal.Scope scope:OrientationJournal.Scope.values()){
                if(scope==current&&scope==OrientationJournal.Scope.MANUAL_CORNERS)continue;
                OrientationJournal journal=new OrientationJournal(context.getFilesDir(),scope);if(!journal.exists())continue;
                if(reader==null){reader=new OrientationVendorBackend(true);reader.resolve();}
                OrientationPilot.State state=reader.read();driver(state);
                if(!state.same(reader.read()))throw new IllegalStateException("The picture is still changing. Try again shortly.");
                journal.saveVerified(state); // Partial/failed receipts cannot pass verifyApplied.
            }
        }finally{OrientationPilot.releaseOpticalOperation();}
    }
    static void save(Context context,OrientationJournal journal,OrientationPilot.State state)throws Exception{
        if(!OrientationPilot.acquireOpticalOperation())throw new IllegalStateException("An adjustment is still running.");
        try{
            OrientationVendorBackend reader=new OrientationVendorBackend(true);reader.resolve();driver(state);
            if(!state.same(reader.read()))throw new IllegalStateException("The picture changed before saving.");
            journal.saveVerified(state);
        }finally{OrientationPilot.releaseOpticalOperation();}
    }
    static void refresh(android.app.Activity activity){if(activity instanceof LauncherSettingsActivity)((LauncherSettingsActivity)activity).projectionSettingSaved();}
    static String message(Throwable failure){
        String why=failure.getMessage()==null?"":failure.getMessage();
        if(why.contains("still")||why.contains("running")||why.contains("finishing")||why.contains("changing"))return "The projector is still adjusting. Try again shortly.";
        if(why.contains("Automatic correction")||why.contains("Automatic correction/recognition"))return "Turn off automatic picture correction first.";
        if(why.contains("Front"))return "Manual alignment is available in Front mode.";
        if(why.contains("Saved angle")||why.contains("Optical profile")||why.contains("full-raster"))return "Scaling is unavailable for the current picture setup.";
        if(why.contains("Auto Focus"))return "Turn on Auto Focus first.";
        if(why.contains("Unfinished")||why.contains("record")||why.contains("journal"))return "An interrupted adjustment needs to be restored.";
        return "Could not finish this adjustment. Try again.";
    }
    private ProjectionWorkflows(){}
}
