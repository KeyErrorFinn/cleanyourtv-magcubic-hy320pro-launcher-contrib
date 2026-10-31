package com.gathin.tvhome;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class ManualCornerTransactionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static OrientationPilot.State original() {
        return new OrientationPilot.State(
                new String[]{"0","100.0","1e2","+0229","560","771","0","539","0","404","400","0","0","0","0","0","0","0","0"},
                new String[]{"Projector_20260713.153921","34","optics","1920x1080","0","0","1","true","1","1","0","0","0,0,0,0,0,0,0,0","0"},
                0,new MirrorField.Value(MirrorField.Path.OEM,0,"0".repeat(64)));
    }
    private static class Journal implements OrientationPilot.Journal {
        boolean conflict,completed; OrientationPilot.State saved,lastState; final List<String> events=new ArrayList<>();
        @Override public boolean exists(){return saved!=null;}
        @Override public boolean conflicts(){return conflict;}
        @Override public void complete(OrientationPilot.State confirmed){completed=true;saved=null;}
        @Override public OrientationPilot.State original(){return saved;}
        @Override public void create(OrientationPilot.State state){assertNull(saved);saved=state;completed=false;}
        @Override public void record(String stage,OrientationPilot.State expected){events.add(stage);lastState=expected;}
        @Override public void verifyApplied(OrientationPilot.State current){
            if(lastState==null||!lastState.same(current)||events.isEmpty()||!events.get(events.size()-1).equals("APPLY_STORED_READBACK_OK_PHYSICAL_UNVERIFIED"))throw new IllegalStateException("Invalid saved receipt");
        }
    }
    private static class Fake implements ManualCornerTransaction.Backend {
        OrientationPilot.State state=original(); final Journal journal;
        int coordinateCalls,propertyCalls,mutationAttempts,failAt; boolean failProperty,gate; int coordinateCode;
        AtomicBoolean cancelAfterCoordinates; int[][] applied;
        Fake(Journal journal){this.journal=journal;}
        @Override public void resolve(){}
        @Override public void prepareWrite(){if(gate)throw new IllegalStateException("Gate");}
        @Override public OrientationPilot.State read(){return state;}
        @Override public int coordinates(int[][] points){
            assertNotNull(journal.saved);assertEquals("BEFORE_COORDINATES",journal.events.get(journal.events.size()-1));
            coordinateCalls++;applied=points;if(++mutationAttempts==failAt)throw new IllegalStateException("Injected coordinate failure");if(cancelAfterCoordinates!=null)cancelAfterCoordinates.set(true);return coordinateCode;
        }
        @Override public void persist(OrientationPilot.Property p,String value){
            propertyCalls++;if(++mutationAttempts==failAt)throw new IllegalStateException("Injected property failure");assertTrue(p.ordinal()>=3 && p.ordinal()<11);
            if(failProperty)throw new IllegalStateException("Denied property");state=state.property(p,value);
        }
    }
    private interface Checked {void run()throws Exception;}
    private static void fails(Checked operation)throws Exception{try{operation.run();fail("Expected stop");}catch(Exception expected){}}
    private static OrientationPilot.State target(OrientationPilot.State state){return state.property(OrientationPilot.Property.D_LTX,"228");}
    @Test public void applyAndExplicitRestoreOnlyChangedCornerPreserveExactState()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);OrientationPilot.State before=f.state;
        ManualCornerTransaction p=new ManualCornerTransaction(f,j,new AtomicBoolean());p.run(before,target(before),false);
        assertTrue(target(before).same(f.state));assertSame(before,j.saved);assertEquals(1,f.propertyCalls);
        p.run(f.state,null,true);assertTrue(before.same(f.state));assertEquals(2,f.coordinateCalls);assertEquals(2,f.propertyCalls);
        assertArrayEquals(new int[]{229,560},f.applied[0]);assertTrue(j.events.get(j.events.size()-1).startsWith("RESTORE_STORED"));
    }
    @Test public void deniedFirstPropertyStillAllowsHardwareRestoreWithoutNoOpPropertyWrite()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);f.failProperty=true;OrientationPilot.State before=f.state;
        ManualCornerTransaction p=new ManualCornerTransaction(f,j,new AtomicBoolean());fails(()->p.run(before,target(before),false));
        assertTrue(p.stage.startsWith("UNKNOWN_PARTIAL"));assertTrue(before.same(f.state));
        p.run(f.state,null,true);assertEquals(2,f.coordinateCalls);assertEquals(1,f.propertyCalls);assertArrayEquals(new int[]{229,560},f.applied[0]);
    }
    @Test public void staleAndNonDisplayChangesStopBeforeMutation()throws Exception{
        for(OrientationPilot.State request:new OrientationPilot.State[]{original(),target(original()).property(OrientationPilot.Property.H_LTX,"1"),target(original()).property(OrientationPilot.Property.ZOOM_H,"100")}){
            Journal j=new Journal();Fake f=new Fake(j);ManualCornerTransaction p=new ManualCornerTransaction(f,j,new AtomicBoolean());
            fails(()->p.run(f.state,request,false));assertEquals(0,f.coordinateCalls);assertFalse(j.exists());
        }
        Journal j=new Journal();Fake f=new Fake(j);ManualCornerTransaction p=new ManualCornerTransaction(f,j,new AtomicBoolean());
        fails(()->p.run(target(f.state),target(f.state),false));assertFalse(j.exists());
    }
    @Test public void unknownCoordinateResultRetainsOriginalAndDoesNotPersist()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);f.coordinateCode=-1;
        ManualCornerTransaction p=new ManualCornerTransaction(f,j,new AtomicBoolean());fails(()->p.run(f.state,target(f.state),false));
        assertEquals(1,f.coordinateCalls);assertEquals(0,f.propertyCalls);assertTrue(j.exists());assertTrue(p.stage.startsWith("UNKNOWN_PARTIAL"));
    }
    @Test public void cancellationAfterCoordinatesStopsPropertiesAndRetainsRecovery()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);AtomicBoolean cancelled=new AtomicBoolean();f.cancelAfterCoordinates=cancelled;
        ManualCornerTransaction p=new ManualCornerTransaction(f,j,cancelled);fails(()->p.run(f.state,target(f.state),false));
        assertEquals(1,f.coordinateCalls);assertEquals(0,f.propertyCalls);assertTrue(j.exists());assertFalse(OrientationPilot.busy());
    }
    @Test public void sharedLockBlocksManualWhileAnotherOpticalOperationOwnsIt()throws Exception{
        assertTrue(OrientationPilot.acquireOpticalOperation());
        try{Journal j=new Journal();Fake f=new Fake(j);ManualCornerTransaction p=new ManualCornerTransaction(f,j,new AtomicBoolean());
            fails(()->p.run(f.state,target(f.state),false));assertEquals(0,f.coordinateCalls);assertTrue(OrientationPilot.busy());
        }finally{OrientationPilot.releaseOpticalOperation();}
    }
    @Test public void manualTransportCanBeFurtherRestrictedNeverEnabledByTests()throws Exception{
        ManualCornerBackend b=new ManualCornerBackend(true);fails(b::prepareWrite);
        fails(()->b.coordinates(new int[4][2]));fails(()->b.persist(OrientationPilot.Property.D_LTX,"228"));
    }
    @Test public void manualJournalNeverReplacesOrArchivesOrientationOriginal()throws Exception{
        java.io.File root=temporary.newFolder();OrientationJournal orientation=new OrientationJournal(root);
        OrientationJournal manual=new OrientationJournal(root,OrientationJournal.Scope.MANUAL_CORNERS);
        orientation.create(original());manual.create(original());manual.record("RESTORE_STORED_READBACK_OK_PHYSICAL_UNVERIFIED",original());manual.complete(original());
        assertFalse(manual.exists());assertTrue(orientation.exists());assertTrue(original().same(orientation.original()));
        assertEquals(1,new java.io.File(root,"manual-corner-history").listFiles().length);
    }
    @Test public void unresolvedOrientationBlocksApplyAndRestoreBeforeAnyMutation()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);ManualCornerTransaction p=new ManualCornerTransaction(f,j,new AtomicBoolean());
        j.conflict=true;fails(()->p.run(f.state,target(f.state),false));assertEquals(0,f.coordinateCalls);assertFalse(j.exists());
        j.create(f.state);fails(()->p.run(f.state,null,true));assertEquals(0,f.coordinateCalls);assertTrue(j.exists());
    }
    @Test public void keepAcceptsVerifiedEditsAndNextRestoreUsesFreshOriginal()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);ManualCornerTransaction p=new ManualCornerTransaction(f,j,new AtomicBoolean());
        OrientationPilot.State before=f.state;p.run(before,target(before),false);
        fails(()->p.complete(before));assertFalse(j.completed);p.complete(f.state);assertTrue(j.completed);assertFalse(j.exists());
        OrientationPilot.State nextOriginal=f.state;
        p.run(f.state,f.state.property(OrientationPilot.Property.D_LTY,"559"),false);
        p.run(f.state,null,true);assertTrue(nextOriginal.same(f.state));p.complete(f.state);assertFalse(j.exists());
    }
    @Test public void defensiveCoordinateValidationRejectsInvalidArguments(){
        ManualCornerBackend.validateCoordinates(OrientationMath.hal(original().display(),1920,1080));
        try{ManualCornerBackend.validateCoordinates(null);fail();}catch(IllegalArgumentException expected){}
        try{ManualCornerBackend.validateCoordinates(new int[][]{{-1,0},{1919,0},{0,1079},{1919,1079}});fail();}catch(IllegalArgumentException expected){}
        try{ManualCornerBackend.validateCoordinates(new int[][]{{1001,0},{1919,0},{0,1079},{1919,1079}});fail();}catch(IllegalArgumentException expected){}
        try{ManualCornerBackend.validateCoordinates(new int[][]{{0,0},{0,0},{0,0},{0,0}});fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void persistedScopesDetectUnresolvedOtherRecords()throws Exception{
        java.io.File root=temporary.newFolder();OrientationJournal orientation=new OrientationJournal(root);
        OrientationJournal manual=new OrientationJournal(root,OrientationJournal.Scope.MANUAL_CORNERS);
        assertFalse(orientation.conflicts());assertFalse(manual.conflicts());
        orientation.create(original());assertTrue(manual.conflicts());assertFalse(orientation.conflicts());
        orientation.record("RESTORE_STORED_READBACK_OK_PHYSICAL_UNVERIFIED",original());orientation.complete(original());
        assertFalse(manual.conflicts());manual.create(original());assertTrue(orientation.conflicts());
    }
    private static OrientationPilot.State multi(OrientationPilot.State original){
        String[] values={"228","559","770","1","538","1","403","399"};
        OrientationPilot.State result=original;
        for(int i=0;i<8;i++)result=result.property(OrientationPilot.Property.values()[3+i],values[i]);
        return result;
    }
    @Test public void allEightOffsetsApplyAndRestoreExactly()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);ManualCornerTransaction p=new ManualCornerTransaction(f,j,new AtomicBoolean());
        OrientationPilot.State before=f.state;p.run(before,multi(before),false);assertTrue(multi(before).same(f.state));assertEquals(8,f.propertyCalls);
        p.run(f.state,null,true);assertTrue(before.same(f.state));assertEquals(16,f.propertyCalls);assertArrayEquals(new int[]{229,560},f.applied[0]);
    }
    @Test public void everyMultiWriteFailureRetainsOriginalAndExplicitRestoreWorks()throws Exception{
        for(int failure=1;failure<=9;failure++){
            Journal j=new Journal();Fake f=new Fake(j);f.failAt=failure;ManualCornerTransaction p=new ManualCornerTransaction(f,j,new AtomicBoolean());
            OrientationPilot.State before=f.state;fails(()->p.run(before,multi(before),false));assertSame(before,j.saved);
            p.run(f.state,null,true);assertTrue(before.same(f.state));assertArrayEquals(new int[]{229,560},f.applied[0]);
        }
    }
    @Test public void setupGeometrySurvivesArchivesAndRejectsMissingHistory()throws Exception{
        java.io.File root=temporary.newFolder();OrientationJournal j=new OrientationJournal(root,OrientationJournal.Scope.MANUAL_CORNERS);
        fails(j::initialGeometry);j.create(original());j.record("RESTORE_STORED_READBACK_OK_PHYSICAL_UNVERIFIED",original());j.complete(original());
        assertTrue(original().same(j.initialGeometry()));
        j.create(multi(original()));j.record("APPLY_STORED_READBACK_OK_PHYSICAL_UNVERIFIED",multi(original()));j.complete(multi(original()));
        assertTrue(original().same(j.initialGeometry()));
    }
    @Test public void liveSessionRetainsFirstOriginalAndRejectsUnownedRecovery()throws Exception {
        Journal j=new Journal();Fake f=new Fake(j);OrientationPilot.State start=f.state;
        ManualCornerTransaction t=new ManualCornerTransaction(f,j,new AtomicBoolean());
        t.live(f.state,f.state.property(OrientationPilot.Property.D_LTX,"228"));
        t.live(f.state,f.state.property(OrientationPilot.Property.D_LTX,"227"));
        assertSame(start,j.saved);
        ManualCornerTransaction other=new ManualCornerTransaction(f,j,new AtomicBoolean());
        fails(()->other.live(f.state,f.state.property(OrientationPilot.Property.D_LTX,"226")));
        t.run(f.state,null,true);assertTrue(start.same(f.state));
    }
    @Test public void failedLiveMoveBlocksContinuationButAllowsRestore()throws Exception {
        Journal j=new Journal();Fake f=new Fake(j);OrientationPilot.State start=f.state;
        ManualCornerTransaction t=new ManualCornerTransaction(f,j,new AtomicBoolean());
        t.live(f.state,f.state.property(OrientationPilot.Property.D_LTX,"228"));
        f.failProperty=true;fails(()->t.live(f.state,f.state.property(OrientationPilot.Property.D_LTX,"227")));
        f.failProperty=false;int calls=f.coordinateCalls;
        fails(()->t.live(f.state,f.state.property(OrientationPilot.Property.D_LTX,"226")));
        assertEquals(calls,f.coordinateCalls);t.run(f.state,null,true);assertTrue(start.same(f.state));
    }
    @Test public void resetAllCornersCanRestoreExactStartingGeometry()throws Exception {
        Journal j=new Journal();Fake f=new Fake(j);OrientationPilot.State start=f.state;
        ManualCornerModel m=new ManualCornerModel(start,1000,1000);
        m.loadOffsets(new String[]{"0","0","0","0","0","0","0","0"});
        ManualCornerTransaction t=new ManualCornerTransaction(f,j,new AtomicBoolean());
        t.live(start,m.staged());for(String v:f.state.display())assertEquals("0",v);
        assertSame(start,j.saved);t.run(f.state,null,true);assertTrue(start.same(f.state));
    }
    @Test public void verifiedResumePreservesOriginalAndRejectsIncompleteOrChangedState()throws Exception {
        Journal j=new Journal();Fake f=new Fake(j);OrientationPilot.State start=f.state;
        ManualCornerTransaction first=new ManualCornerTransaction(f,j,new AtomicBoolean());
        first.live(start,start.property(OrientationPilot.Property.D_LTX,"228"));
        ManualCornerTransaction next=new ManualCornerTransaction(f,j,new AtomicBoolean());
        next.resume(f.state);assertSame(start,j.saved);
        next.live(f.state,f.state.property(OrientationPilot.Property.D_LTX,"227"));assertSame(start,j.saved);
        j.record("UNKNOWN_PARTIAL",f.state);
        fails(()->next.resume(f.state));int calls=f.coordinateCalls;
        fails(()->next.live(f.state,f.state.property(OrientationPilot.Property.D_LTX,"226")));assertEquals(calls,f.coordinateCalls);
        next.run(f.state,null,true);assertTrue(start.same(f.state));
    }
    @Test public void durableResumeCheckIsReadOnlyAndRejectsIncompleteOrDriftedReceipts()throws Exception {
        java.io.File root=temporary.newFolder();OrientationJournal journal=new OrientationJournal(root,OrientationJournal.Scope.MANUAL_CORNERS);
        OrientationPilot.State start=original(),target=target(start);journal.create(start);
        fails(()->journal.verifyApplied(start));
        journal.record("APPLY_STORED_READBACK_OK_PHYSICAL_UNVERIFIED",target);
        byte[] originalBytes=java.nio.file.Files.readAllBytes(new java.io.File(root,"manual-corners/original.bin").toPath());
        journal.verifyApplied(target);assertTrue(journal.exists());assertTrue(start.same(journal.original()));
        assertArrayEquals(originalBytes,java.nio.file.Files.readAllBytes(new java.io.File(root,"manual-corners/original.bin").toPath()));
        fails(()->journal.verifyApplied(start));journal.record("BEFORE_COORDINATES",target);fails(()->journal.verifyApplied(target));
        journal.record("APPLY_STORED_READBACK_OK_PHYSICAL_UNVERIFIED",target);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(root,"manual-corners/events.bin"),true)){out.write(1);}
        fails(()->journal.verifyApplied(target));assertTrue(journal.exists());
    }
}
