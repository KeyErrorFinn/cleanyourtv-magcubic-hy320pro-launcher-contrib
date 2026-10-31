package com.gathin.tvhome;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class ProjectionSettingsWorkflowTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    static final class Hardware implements ProjectionScale.Backend,ManualCornerTransaction.Backend {
        OrientationPilot.State state=ProjectionOperationsTest.original();
        int[] actual=ProjectionOperationsTest.flat(OrientationMath.hal(state.display(),1920,1080));boolean failCoordinates;
        public void resolve(){}public void prepareWrite(){}public OrientationPilot.State read(){return state;}public int[] driver(){return actual.clone();}
        public int coordinates(int[][] points){actual=ProjectionOperationsTest.flat(points);if(failCoordinates)throw new IllegalStateException("Interrupted after driver write");return 0;}
        public void persist(OrientationPilot.Property property,String value){state=state.property(property,value);}
        public void persistZoom(ProjectionScaleBackend.Key key,String value){state=key==ProjectionScaleBackend.Key.STEP?state.zoomStep(value):state.zoomVector(value);}
    }
    @Test public void applySaveThenManualAdjustmentWorksForEveryScalingStep()throws Exception{
        for(int step=0;step<=20;step++){
            File files=temporary.newFolder();Hardware hardware=new Hardware();
            OrientationJournal scaling=new OrientationJournal(files,OrientationJournal.Scope.SCALING);
            ProjectionScale scale=new ProjectionScale(hardware,scaling,new AtomicBoolean());
            if(step>0){scale.run(hardware.state,step,false);assertTrue(scaling.exists());scaling.saveVerified(scale.inspect());}
            assertFalse(scaling.exists());OrientationJournal manual=new OrientationJournal(files,OrientationJournal.Scope.MANUAL_CORNERS);assertFalse(manual.conflicts());
            ManualCornerTransaction editor=new ManualCornerTransaction(hardware,manual,new AtomicBoolean());OrientationPilot.State before=editor.inspect(false);
            String[] base=before.htc();ManualCornerModel model=new ManualCornerModel(before,1000,1000);assertTrue(model.step(0,0,1));editor.live(before,model.staged());
            assertTrue(model.staged().same(hardware.state));assertEquals(Integer.toString(step),hardware.state.guard(OrientationPilot.Guard.ZOOM_VALUE));assertEquals(ProjectionScaleMath.vector(step),hardware.state.guard(OrientationPilot.Guard.VECTOR));assertArrayEquals(base,hardware.state.htc());
        }
    }
    @Test public void partialScalingCannotBeAutomaticallySavedOrUnlockManualEditing()throws Exception{
        File files=temporary.newFolder();Hardware hardware=new Hardware();OrientationJournal journal=new OrientationJournal(files,OrientationJournal.Scope.SCALING);hardware.failCoordinates=true;
        try{new ProjectionScale(hardware,journal,new AtomicBoolean()).run(hardware.state,1,false);fail();}catch(IllegalStateException expected){}
        try{journal.saveVerified(hardware.state);fail();}catch(IOException expected){}
        assertTrue(journal.exists());assertTrue(new OrientationJournal(files,OrientationJournal.Scope.MANUAL_CORNERS).conflicts());
    }
    @Test public void normalSaveRetainsExactOriginalWithoutClaimingPhysicalOwnerVerification()throws Exception{
        File files=temporary.newFolder();Hardware hardware=new Hardware();OrientationJournal journal=new OrientationJournal(files,OrientationJournal.Scope.SCALING);
        ProjectionScale scale=new ProjectionScale(hardware,journal,new AtomicBoolean());scale.run(hardware.state,2,false);
        byte[] original=Files.readAllBytes(new File(files,"projection-scaling/original.bin").toPath());journal.saveVerified(scale.inspect());
        File[] history=new File(files,"projection-scaling-history").listFiles();assertNotNull(history);assertEquals(1,history.length);
        assertArrayEquals(original,Files.readAllBytes(new File(history[0],"original.bin").toPath()));
        String events=new String(Files.readAllBytes(new File(history[0],"events.bin").toPath()),java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(events.contains("SETTINGS_SAVED_READBACK_ONLY"));assertFalse(events.contains("OWNER_CONFIRMED_PICTURE"));
    }
    private static AutomaticProjectionControl.Snapshot status(String request,String queued,String active,String angle,OrientationPilot.State state){return new AutomaticProjectionControl.Snapshot(state,"0","1","1","1",request,queued,active,angle);}
    @Test public void automaticBookkeepingRequiresQuietUnchangedSnapshots(){
        OrientationPilot.State geometry=ProjectionOperationsTest.original();AutomaticProjectionControl.Snapshot idle=status("0","0","0","45.0",geometry);
        assertTrue(ProjectionWorkflows.settled(idle,status("0","0","0","45.0",geometry)));
        assertFalse(ProjectionWorkflows.settled(idle,status("1","0","0","45.0",geometry)));
        assertFalse(ProjectionWorkflows.settled(idle,status("0","1","0","45.0",geometry)));
        assertFalse(ProjectionWorkflows.settled(idle,status("0","0","1","45.0",geometry)));
        assertFalse(ProjectionWorkflows.settled(idle,status("0","0","0","0.0",geometry)));
        assertFalse(ProjectionWorkflows.settled(idle,status("0","0","0","45.0",geometry.property(OrientationPilot.Property.D_LTX,"221"))));
    }
    @Test public void requestCoolDownDoesNotInferCompletionFromImmediatelyConsumedFlag()throws Exception{
        for(AutomaticProjectionControl.Action action:AutomaticProjectionControl.Action.values()){
            File files=temporary.newFolder();AutomaticProjectionJournal journal=new AutomaticProjectionJournal(files);journal.capture(action,status("0","0","0","45.0",ProjectionOperationsTest.original()));
            long started;try(DataInputStream in=new DataInputStream(new FileInputStream(new File(files,"projection-automatic/original.bin")))){in.readUTF();in.readUTF();started=in.readLong();}
            long wait=action==AutomaticProjectionControl.Action.CALIBRATE?35000:5000;
            assertFalse(journal.maySettle(started-1));assertFalse(journal.maySettle(started+wait-1));assertTrue(journal.maySettle(started+wait));
            journal.settled();assertFalse(journal.exists());assertEquals(1,new File(files,"projection-automatic-history").listFiles().length);
        }
    }
}
