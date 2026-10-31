package com.gathin.tvhome;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.util.*;
import java.io.*;
import static org.junit.Assert.*;

public class AutomaticProjectionControlTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    static AutomaticProjectionControl.Snapshot snapshot(String auto,String focus,String request,String queued,String running){return new AutomaticProjectionControl.Snapshot(ProjectionOperationsTest.original(),auto,focus,"1","1",request,queued,running,"45.0");}
    static final class Journal implements AutomaticProjectionControl.Journal {
        boolean saved,failCapture;final List<String> events=new ArrayList<>();
        public boolean exists(){return saved;}
        public void capture(AutomaticProjectionControl.Action action,AutomaticProjectionControl.Snapshot before)throws Exception{if(failCapture)throw new IOException("Cannot fsync");assertFalse(saved);saved=true;events.add("CAPTURE");}
        public void record(String event){assertTrue(saved);events.add(event);}
    }
    static final class Fake implements AutomaticProjectionControl.Backend {
        AutomaticProjectionControl.Snapshot state=snapshot("0","1","0","0","0");
        final Journal journal;boolean conflict,denied,drop,broadcastFailure;int preference,correction,calibration;
        Fake(Journal journal){this.journal=journal;}
        public AutomaticProjectionControl.Snapshot read(){return state;}
        public boolean conflict(){return conflict;}
        public void automatic(boolean enabled){assertTrue(journal.saved);preference++;if(denied)throw new SecurityException("Denied");if(!drop)state=snapshot(enabled?"1":"0",state.focus,state.request,state.queued,state.running);}
        public void requestCorrection(){assertTrue(journal.saved);assertEquals("1",state.automatic);correction++;if(broadcastFailure)throw new SecurityException("Broadcast denied");}
        public void requestCalibration(){assertTrue(journal.saved);calibration++;if(denied)throw new SecurityException("Denied");/* OEM may consume the flag immediately; do not infer completion. */}
    }
    interface Checked {void run()throws Exception;}
    static void fails(Checked operation)throws Exception{try{operation.run();fail("Expected stopped request");}catch(Exception expected){}}
    @Test public void enableCapturesThenConfirmsPreferenceBeforeBroadcast()throws Exception{
        Journal journal=new Journal();Fake fake=new Fake(journal);new AutomaticProjectionControl(fake,journal).run(AutomaticProjectionControl.Action.ENABLE,fake.state);
        assertEquals("CAPTURE",journal.events.get(0));assertEquals(1,fake.preference);assertEquals(1,fake.correction);assertEquals(0,fake.calibration);assertTrue(journal.saved);
        assertTrue(journal.events.contains("PREFERENCE_CONFIRMED_1"));assertEquals("REQUEST_SENT_PHYSICAL_RESULT_UNVERIFIED",journal.events.get(journal.events.size()-1));
    }
    @Test public void disableDuringNativeActivityChangesPreferenceOnlyAndNeverPretendsToCancel()throws Exception{
        Journal journal=new Journal();Fake fake=new Fake(journal);fake.state=snapshot("1","1","0","0","1");
        new AutomaticProjectionControl(fake,journal).run(AutomaticProjectionControl.Action.DISABLE,fake.state);
        assertEquals("0",fake.state.automatic);assertEquals("1",fake.state.running);assertEquals(0,fake.correction);assertEquals(0,fake.calibration);assertTrue(journal.events.contains("AUTOMATIC_DISABLED_NO_FORCED_GEOMETRY_OR_MOTOR_STOP"));
    }
    @Test public void samePreferenceDoesNotCreateHistoryOrRequest()throws Exception{
        Journal journal=new Journal();Fake fake=new Fake(journal);new AutomaticProjectionControl(fake,journal).run(AutomaticProjectionControl.Action.DISABLE,fake.state);
        assertFalse(journal.saved);assertEquals(0,fake.preference+fake.correction+fake.calibration);
    }
    @Test public void consumedCalibrationFlagStillMeansRequestOnly()throws Exception{
        Journal journal=new Journal();Fake fake=new Fake(journal);new AutomaticProjectionControl(fake,journal).run(AutomaticProjectionControl.Action.CALIBRATE,fake.state);
        assertEquals(1,fake.calibration);assertEquals(0,fake.preference+fake.correction);assertEquals("CALIBRATION_REQUEST_SENT_NOT_COMPLETION",journal.events.get(journal.events.size()-1));assertTrue(journal.saved);
    }
    @Test public void calibrationRejectsFocusOffActivityAndPriorHistory()throws Exception{
        for(int mode=0;mode<5;mode++){Journal j=new Journal();Fake f=new Fake(j);if(mode==0)f.state=snapshot("0","0","0","0","0");if(mode==1)f.state=snapshot("0","1","1","0","0");if(mode==2)f.state=snapshot("0","1","0","1","0");if(mode==3)f.state=snapshot("0","1","0","0","1");if(mode==4)j.saved=true;
            fails(()->new AutomaticProjectionControl(f,j).run(AutomaticProjectionControl.Action.CALIBRATE,f.state));assertEquals(0,f.preference+f.correction+f.calibration);}
    }
    @Test public void staleSelectionAndOtherGeometryJournalStopBeforeCapture()throws Exception{
        for(boolean conflict:new boolean[]{false,true}){Journal j=new Journal();Fake f=new Fake(j);f.conflict=conflict;AutomaticProjectionControl.Snapshot reviewed=conflict?f.state:snapshot("1","1","0","0","0");fails(()->new AutomaticProjectionControl(f,j).run(AutomaticProjectionControl.Action.ENABLE,reviewed));assertFalse(j.saved);assertEquals(0,f.preference+f.correction+f.calibration);}
    }
    @Test public void failedDurableCapturePreventsEveryWrite()throws Exception{
        Journal j=new Journal();j.failCapture=true;Fake f=new Fake(j);fails(()->new AutomaticProjectionControl(f,j).run(AutomaticProjectionControl.Action.ENABLE,f.state));assertEquals(0,f.preference+f.correction+f.calibration);
    }
    @Test public void deniedOrDroppedPreferenceNeverDispatchesCorrection()throws Exception{
        for(boolean drop:new boolean[]{false,true}){Journal j=new Journal();Fake f=new Fake(j);f.drop=drop;f.denied=!drop;fails(()->new AutomaticProjectionControl(f,j).run(AutomaticProjectionControl.Action.ENABLE,f.state));assertTrue(j.saved);assertEquals(0,f.correction);assertTrue(j.events.get(j.events.size()-1).startsWith("STOPPED_UNKNOWN_PARTIAL"));}
    }
    @Test public void deniedBroadcastRetainsHistoryWithoutRetryOrFalseSuccess()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);f.broadcastFailure=true;fails(()->new AutomaticProjectionControl(f,j).run(AutomaticProjectionControl.Action.ENABLE,f.state));assertEquals(1,f.correction);assertTrue(j.saved);assertEquals("1",f.state.automatic);assertTrue(j.events.get(j.events.size()-1).startsWith("STOPPED_UNKNOWN_PARTIAL"));
    }
    @Test public void offRemainsAvailableDuringPendingOwnRequest()throws Exception{
        Journal j=new Journal();j.saved=true;Fake f=new Fake(j);f.state=snapshot("1","1","0","0","0");new AutomaticProjectionControl(f,j).run(AutomaticProjectionControl.Action.DISABLE,f.state);assertEquals(1,f.preference);assertFalse(j.events.contains("CAPTURE"));
    }
    @Test public void offDoesNotRequireStaleGeometryOrWorkingCamera()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);AutomaticProjectionControl.Snapshot stale=f.state;
        f.state=new AutomaticProjectionControl.Snapshot(ProjectionOperationsTest.original().property(OrientationPilot.Property.D_LTX,"221"),"1","1","0","0","0","0","1","45.0");
        new AutomaticProjectionControl(f,j).run(AutomaticProjectionControl.Action.DISABLE,stale);assertEquals(1,f.preference);assertEquals(0,f.correction+f.calibration);
    }
    @Test public void opticalLockBlocksConcurrentRequests()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);assertTrue(OrientationPilot.acquireOpticalOperation());try{fails(()->new AutomaticProjectionControl(f,j).run(AutomaticProjectionControl.Action.ENABLE,f.state));assertFalse(j.saved);}finally{OrientationPilot.releaseOpticalOperation();}
    }
    @Test public void durableHistoryBlocksAllGeometryScopesAndArchivesWithoutDeletingOriginal()throws Exception{
        File files=temporary.newFolder();AutomaticProjectionJournal journal=new AutomaticProjectionJournal(files);assertFalse(journal.exists());journal.capture(AutomaticProjectionControl.Action.CALIBRATE,snapshot("0","1","0","0","0"));
        File original=new File(files,"projection-automatic/original.bin");byte[] before=java.nio.file.Files.readAllBytes(original.toPath());
        for(OrientationJournal.Scope scope:OrientationJournal.Scope.values())assertTrue(new OrientationJournal(files,scope).conflicts());
        fails(()->journal.capture(AutomaticProjectionControl.Action.ENABLE,snapshot("0","1","0","0","0")));assertArrayEquals(before,java.nio.file.Files.readAllBytes(original.toPath()));journal.finish();assertFalse(journal.exists());
        File[] archives=new File(files,"projection-automatic-history").listFiles();assertNotNull(archives);assertEquals(1,archives.length);assertArrayEquals(before,java.nio.file.Files.readAllBytes(new File(archives[0],"original.bin").toPath()));
        for(OrientationJournal.Scope scope:OrientationJournal.Scope.values())assertFalse(new OrientationJournal(files,scope).conflicts());
    }
}
