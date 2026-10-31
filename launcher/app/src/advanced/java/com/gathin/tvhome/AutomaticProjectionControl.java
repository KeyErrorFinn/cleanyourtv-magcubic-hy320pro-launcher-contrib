package com.gathin.tvhome;

/** Typed manufacturer requests. Receipt means requested, never optical success or motor cancellation. */
final class AutomaticProjectionControl {
    enum Action { ENABLE, DISABLE, CALIBRATE }
    interface Backend {
        Snapshot read() throws Exception;
        boolean conflict();
        void automatic(boolean enabled) throws Exception;
        void requestCorrection() throws Exception;
        void requestCalibration() throws Exception;
    }
    interface Journal { boolean exists();void capture(Action action,Snapshot before)throws Exception;void record(String event)throws Exception; }
    static final class Snapshot {
        final OrientationPilot.State geometry;
        final String automatic,focus,camera,direction,request,queued,running,angle;
        Snapshot(OrientationPilot.State geometry,String automatic,String focus,String camera,String direction,String request,String queued,String running,String angle){
            this.geometry=geometry;this.automatic=bit(automatic);this.focus=bit(focus);this.camera=bit(camera);this.direction=bit(direction);
            this.request=bit(request);this.queued=bit(queued);this.running=bit(running);this.angle=angle;
            double value=Double.parseDouble(angle);if(!Double.isFinite(value)||Math.abs(value)>45)throw new IllegalArgumentException("Unknown correction angle");
        }
        private static String bit(String value){if(!"0".equals(value)&&!"1".equals(value))throw new IllegalArgumentException("Unknown manufacturer status");return value;}
        boolean noRequest(){return "0".equals(request)&&"0".equals(queued)&&"0".equals(running);}
        boolean same(Snapshot other){return other!=null&&geometry.same(other.geometry)&&automatic.equals(other.automatic)&&focus.equals(other.focus)&&camera.equals(other.camera)&&direction.equals(other.direction)&&request.equals(other.request)&&queued.equals(other.queued)&&running.equals(other.running)&&angle.equals(other.angle);}
    }
    private final Backend backend;private final Journal journal;
    AutomaticProjectionControl(Backend backend,Journal journal){this.backend=backend;this.journal=journal;}
    void run(Action action,Snapshot reviewed)throws Exception{
        if(!OrientationPilot.acquireOpticalOperation())throw new IllegalStateException("Another projection operation is running");
        try{
            if(backend.conflict())throw new IllegalStateException("Keep or restore the other projection change first");
            Snapshot before=backend.read();
            if(action!=Action.DISABLE&&(!before.same(reviewed)||!before.same(backend.read())))throw new IllegalStateException("Projection changed; refresh before continuing");
            // Turning automatic correction off remains available; it is NOT a motor-stop operation.
            if(action!=Action.DISABLE&&!before.noRequest())throw new IllegalStateException("A manufacturer request/activity is reported; wait and refresh");
            if(action!=Action.DISABLE&&(!"1".equals(before.camera)||!"1".equals(before.direction)))throw new IllegalStateException("This control requires the reviewed focus-up/down hardware branch");
            if(action==Action.CALIBRATE&&(!"1".equals(before.focus)||journal.exists()))throw new IllegalStateException("Enable Auto Focus and finish the previous request first");
            String target=action==Action.ENABLE?"1":"0";
            if(action!=Action.CALIBRATE&&target.equals(before.automatic))return;
            if(action==Action.ENABLE&&journal.exists())throw new IllegalStateException("Finish the previous request first");
            if(!journal.exists())journal.capture(action,before);
            journal.record("BEFORE_"+action.name());
            if(action==Action.CALIBRATE){backend.requestCalibration();journal.record("CALIBRATION_REQUEST_SENT_NOT_COMPLETION");}
            else{
                backend.automatic(action==Action.ENABLE);
                if(!target.equals(backend.read().automatic))throw new IllegalStateException("Automatic correction preference was not confirmed");
                journal.record("PREFERENCE_CONFIRMED_"+target);
                if(action==Action.ENABLE)backend.requestCorrection();
                else journal.record("AUTOMATIC_DISABLED_NO_FORCED_GEOMETRY_OR_MOTOR_STOP");
                journal.record("REQUEST_SENT_PHYSICAL_RESULT_UNVERIFIED");
            }
        }catch(Exception failure){if(journal.exists())try{journal.record("STOPPED_UNKNOWN_PARTIAL_"+failure.getClass().getSimpleName());}catch(Exception logging){failure.addSuppressed(logging);}throw failure;}
        finally{OrientationPilot.releaseOpticalOperation();}
    }
}
