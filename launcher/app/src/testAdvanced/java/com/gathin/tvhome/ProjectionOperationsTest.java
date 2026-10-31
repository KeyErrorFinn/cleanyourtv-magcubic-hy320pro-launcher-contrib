package com.gathin.tvhome;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class ProjectionOperationsTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    static OrientationPilot.State original(){return new OrientationPilot.State(
        new String[]{"0","100.0","1e2","+0220","560","779","1","527","1","428","404","1","2","3","4","5","6","7","8"},
        new String[]{"Projector_20260713.153921","34","optics","1920x1080","0","0","1","true","1","1","0","0","0,0,0,0,0,0,0,0","0"},
        0,new MirrorField.Value(MirrorField.Path.OEM,0,"0".repeat(64)));}
    static int[] flat(int[][] points){int[] result=new int[8];for(int i=0;i<4;i++){result[2*i]=points[i][0];result[2*i+1]=points[i][1];}return result;}
    static final class Journal implements OrientationPilot.Journal {
        OrientationPilot.State saved,last;String stage;boolean conflict;
        public boolean exists(){return saved!=null;}public boolean conflicts(){return conflict;}
        public OrientationPilot.State original(){return saved;}
        public void create(OrientationPilot.State state){assertNull(saved);saved=state;}
        public void record(String stage,OrientationPilot.State state){this.stage=stage;last=state;}
        public void complete(OrientationPilot.State state){assertTrue(stage.endsWith("STORED_READBACK_OK_PHYSICAL_UNVERIFIED"));assertTrue(last.same(state));saved=null;}
    }
    static final class Fake implements ProjectionScale.Backend {
        OrientationPilot.State state=original();int[] actual=flat(OrientationMath.hal(state.display(),1920,1080));
        final Journal journal;int writes,failAt,coordinates,properties,zoomWrites;boolean mismatchAfter,denied;
        Fake(Journal journal){this.journal=journal;}
        public void resolve(){}public void prepareWrite(){if(denied)throw new IllegalStateException("Denied");}
        public OrientationPilot.State read(){return state;}public int[] driver(){return actual.clone();}
        void mutation(){assertNotNull(journal.saved);if(++writes==failAt)throw new IllegalStateException("Injected partial mutation");}
        public int coordinates(int[][] points){coordinates++;actual=flat(points);if(mismatchAfter)actual[0]++;mutation();return 0;}
        public void persist(OrientationPilot.Property key,String value){properties++;state=state.property(key,value);mutation();}
        public void persistZoom(ProjectionScaleBackend.Key key,String value){zoomWrites++;state=key==ProjectionScaleBackend.Key.STEP?state.zoomStep(value):state.zoomVector(value);mutation();}
    }
    interface Checked{void run()throws Exception;}
    static void fails(Checked operation)throws Exception{try{operation.run();fail("Expected rejection");}catch(Exception expected){}}
    @Test public void saved45DegreeProfileMatchesAllStockScalingSteps(){
        int[][] fixtures={{269,381,269,381,0,0,0,0},{273,382,273,382,8,3,7,3},{277,383,277,383,16,8,15,8},{281,384,281,384,24,12,23,12},{285,385,285,385,32,18,31,18},{290,386,290,386,40,21,39,21},{294,387,294,387,48,27,48,27},{298,388,298,388,56,31,56,31},{303,389,303,389,64,35,64,35},{307,390,307,390,72,39,71,39},{311,391,311,391,80,45,80,45},{316,392,316,392,88,49,87,49},{320,393,320,393,96,53,96,53},{324,394,324,394,103,57,103,57},{329,395,329,395,111,62,111,62},{333,396,333,396,120,66,120,66},{338,397,338,397,127,71,127,71},{342,398,342,398,136,76,135,76},{347,399,347,399,144,80,143,80},{351,400,351,400,152,84,151,84},{356,401,356,401,160,90,159,90}};
        for(int step=0;step<fixtures.length;step++){String[] actual=ProjectionScaleMath.offsets(step,45d);for(int i=0;i<8;i++)assertEquals("step "+step+" field "+i,Integer.toString(fixtures[step][i]),actual[i]);}
    }
    @Test public void stockFinalConversionFixturesForEveryStep(){
        int[][] fixtures={{0,4,0,4,0,0,0,0},{8,8,7,8,8,4,7,4},{15,13,15,13,15,8,15,8},{23,17,23,17,23,12,23,12},{31,22,31,22,31,17,31,17},{39,26,39,26,39,21,39,21},{48,31,47,31,48,27,47,27},{55,35,56,35,55,31,56,31},{64,40,64,40,64,35,64,35},{72,44,71,44,72,40,71,40},{80,49,80,49,80,45,80,45},{88,53,87,53,88,49,87,49},{96,58,96,58,96,53,96,53},{103,62,103,62,103,57,103,57},{111,67,111,67,111,63,111,63},{119,71,119,71,119,66,119,66},{127,76,127,76,127,71,127,71},{135,80,135,80,135,76,135,76},{144,85,143,85,144,81,143,81},{152,88,151,88,152,84,151,84},{160,93,159,93,160,90,159,90}};
        for(int step=0;step<fixtures.length;step++){String[] actual=ProjectionScaleMath.offsets(step);for(int i=0;i<8;i++)assertEquals("step "+step+" field "+i,Integer.toString(fixtures[step][i]),actual[i]);}
    }
    @Test public void vectorPreservesSeparateStockIntegerOffsets(){assertEquals("8,4,8,4,8,4,8,4",ProjectionScaleMath.vector(1));assertEquals("160,90,160,90,160,90,160,90",ProjectionScaleMath.vector(20));}
    @Test(expected=IllegalArgumentException.class)public void changedOpticalProfileIsRejected(){ProjectionScaleMath.optics("26.103256","15.472923","0");}
    @Test public void resetBothModelsAndRestoreExactOriginal()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);OrientationPilot.State before=f.state;ProjectionReset reset=new ProjectionReset(f,j,new AtomicBoolean());
        reset.run(before,false);for(int i=3;i<19;i++)assertEquals(0,Integer.parseInt(f.state.value(OrientationPilot.Property.values()[i])));
        assertEquals(16,f.properties);assertEquals(0,f.zoomWrites);assertSame(before,j.saved);
        reset.run(f.state,true);assertTrue(before.same(f.state));assertArrayEquals(flat(OrientationMath.hal(before.display(),1920,1080)),f.actual);
    }
    @Test public void resetFromScaledStateRestoresZoomAndBothModels()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);f.state=ProjectionScaleMath.target(f.state,20);f.actual=flat(OrientationMath.hal(f.state.display(),1920,1080));OrientationPilot.State before=f.state;
        ProjectionReset reset=new ProjectionReset(f,j,new AtomicBoolean());reset.run(before,false);
        assertEquals("0",f.state.guard(OrientationPilot.Guard.ZOOM_VALUE));assertEquals(ProjectionScaleMath.vector(0),f.state.guard(OrientationPilot.Guard.VECTOR));
        reset.run(f.state,true);assertTrue(before.same(f.state));
    }
    @Test public void resetEveryPartialWriteCanRestoreOriginal()throws Exception{
        for(int failAt=1;failAt<=17;failAt++){Journal j=new Journal();Fake f=new Fake(j);f.failAt=failAt;OrientationPilot.State before=f.state;ProjectionReset reset=new ProjectionReset(f,j,new AtomicBoolean());
            fails(()->reset.run(before,false));assertTrue(j.exists());assertTrue(reset.stage.startsWith("UNKNOWN_PARTIAL"));reset.run(f.state,true);assertTrue("failure "+failAt,before.same(f.state));}
    }
    @Test public void scalingEveryPartialWriteCanRestoreOriginal()throws Exception{
        for(int failAt=1;failAt<=11;failAt++){Journal j=new Journal();Fake f=new Fake(j);f.failAt=failAt;OrientationPilot.State before=f.state;ProjectionScale scale=new ProjectionScale(f,j,new AtomicBoolean());
            fails(()->scale.run(before,20,false));assertTrue(j.exists());scale.run(f.state,0,true);assertTrue("failure "+failAt,before.same(f.state));}
    }
    @Test public void scalePreservesBaseAndRestorePreservesRawEncodings()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);OrientationPilot.State before=f.state;ProjectionScale scale=new ProjectionScale(f,j,new AtomicBoolean());scale.run(before,20,false);
        assertEquals("20",f.state.guard(OrientationPilot.Guard.ZOOM_VALUE));for(int i=11;i<19;i++)assertEquals(before.value(OrientationPilot.Property.values()[i]),f.state.value(OrientationPilot.Property.values()[i]));
        assertEquals("1e2",f.state.value(OrientationPilot.Property.ZOOM_V));scale.run(f.state,0,true);assertTrue(before.same(f.state));assertEquals("+0220",f.state.value(OrientationPilot.Property.D_LTX));
    }
    @Test public void sameStepDoesNotEraseManualGeometry()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);fails(()->new ProjectionScale(f,j,new AtomicBoolean()).run(f.state,0,false));assertEquals(0,f.writes);assertFalse(j.exists());
    }
    @Test public void driverMismatchStopsBeforePropertyWritesAndAllowsRestore()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);f.mismatchAfter=true;OrientationPilot.State before=f.state;ProjectionScale scale=new ProjectionScale(f,j,new AtomicBoolean());
        fails(()->scale.run(before,20,false));assertEquals(0,f.properties);assertEquals(0,f.zoomWrites);f.mismatchAfter=false;scale.run(f.state,0,true);assertTrue(before.same(f.state));
    }
    @Test public void badInitialDriverDoesNotCreateJournal()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);f.actual[0]++;fails(()->new ProjectionReset(f,j,new AtomicBoolean()).run(f.state,false));assertEquals(0,f.writes);assertFalse(j.exists());
    }
    @Test public void staleAndDeniedSelectionsDoNotMutate()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);ProjectionScale scale=new ProjectionScale(f,j,new AtomicBoolean());fails(()->scale.run(f.state.property(OrientationPilot.Property.D_LTX,"221"),20,false));assertFalse(j.exists());
        f.denied=true;fails(()->scale.run(f.state,20,false));assertFalse(j.exists());assertEquals(0,f.writes);
    }
    @Test public void manualEditAtScaleKeepsMetadataAndLimits()throws Exception{
        OrientationPilot.State state=ProjectionScaleMath.target(original(),20);ManualCornerModel model=new ManualCornerModel(state,1000,1000);assertTrue(model.step(0,0,1));
        assertEquals("20",model.staged().guard(OrientationPilot.Guard.ZOOM_VALUE));assertEquals(state.guard(OrientationPilot.Guard.VECTOR),model.staged().guard(OrientationPilot.Guard.VECTOR));ManualCornerTransaction.validateChange(state,model.staged());
    }
    @Test public void everyStepUsesTheStockPanelPermutations(){
        for(int mode=0;mode<4;mode++)for(int step=0;step<=20;step++){
            OrientationPilot.State before=original().target(mode),after=ProjectionScaleMath.target(before,step);
            assertArrayEquals(OrientationMath.permute(ProjectionScaleMath.offsets(step,45d),0,mode),after.display());
            assertEquals(mode,after.mode());assertEquals(before.mirror,after.mirror);after.validate(true);
        }
    }
    @Test public void orientationChangeRetainsRecognisedScalingMetadata(){
        OrientationPilot.State scaled=ProjectionScaleMath.target(original(),20);
        for(int mode=0;mode<4;mode++){OrientationPilot.State target=scaled.target(mode);target.validate(true);assertEquals("20",target.guard(OrientationPilot.Guard.ZOOM_VALUE));assertEquals(ProjectionScaleMath.vector(20),target.guard(OrientationPilot.Guard.VECTOR));}
    }
    @Test public void unexpectedSavedBaseChangeBlocksScaleRestore()throws Exception{
        Journal j=new Journal();Fake f=new Fake(j);ProjectionScale scale=new ProjectionScale(f,j,new AtomicBoolean());scale.run(f.state,20,false);f.state=f.state.property(OrientationPilot.Property.H_LTX,"999");int writes=f.writes;
        fails(()->scale.run(f.state,0,true));assertEquals(writes,f.writes);assertTrue(j.exists());
    }
    @Test public void allJournalScopesInterlockAndArchiveIndependently()throws Exception{
        for(OrientationJournal.Scope scope:OrientationJournal.Scope.values()){
            java.io.File root=temporary.newFolder();OrientationJournal journal=new OrientationJournal(root,scope);OrientationPilot.State state=original();journal.create(state);
            for(OrientationJournal.Scope other:OrientationJournal.Scope.values())assertEquals(other!=scope,new OrientationJournal(root,other).conflicts());
            journal.record("APPLY_STORED_READBACK_OK_PHYSICAL_UNVERIFIED",state);journal.complete(state);assertFalse(journal.exists());
            for(OrientationJournal.Scope other:OrientationJournal.Scope.values())assertFalse(new OrientationJournal(root,other).conflicts());
        }
    }
    @Test public void scaledManualJournalRoundTripsWithoutChangingOldFormat()throws Exception{
        java.io.File root=temporary.newFolder();OrientationJournal journal=new OrientationJournal(root,OrientationJournal.Scope.MANUAL_CORNERS);OrientationPilot.State scaled=ProjectionScaleMath.target(original(),20);journal.create(scaled);
        assertTrue(scaled.same(new OrientationJournal(root,OrientationJournal.Scope.MANUAL_CORNERS).original()));journal.record("APPLY_STORED_READBACK_OK_PHYSICAL_UNVERIFIED",scaled);journal.complete(scaled);assertFalse(journal.exists());
    }
}
