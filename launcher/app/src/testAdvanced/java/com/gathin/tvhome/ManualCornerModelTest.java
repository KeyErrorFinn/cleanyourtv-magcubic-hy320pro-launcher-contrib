package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class ManualCornerModelTest {
    private static OrientationPilot.State original() {
        String[] values = {"0", "100.0", "1e2", "+0229", "560", "771", "0", "539", "0", "404", "400",
                "0", "0", "0", "0", "0", "0", "0", "0"};
        String[] guards = {"Projector_20260713.153921", "34", "optics", "1920x1080", "0", "0", "1", "true", "1", "1", "0", "0", "0,0,0,0,0,0,0,0", "0"};
        return new OrientationPilot.State(values, guards, 0, new MirrorField.Value(MirrorField.Path.OEM, 0, "0".repeat(64)));
    }
    private interface Checked { void run() throws Exception; }
    private static void fails(Checked operation) throws Exception {
        try { operation.run(); fail("Expected rejection"); } catch (IllegalArgumentException | java.io.IOException expected) { }
    }
    @Test public void discardPreservesExactOriginalEncodingsAndOtherModels() {
        OrientationPilot.State before = original(); ManualCornerModel model = new ManualCornerModel(before,1000,1000);
        assertTrue(model.step(0,0,-1)); assertEquals(228,model.value(0,0)); assertTrue(model.changed());
        assertEquals("+0229", before.value(OrientationPilot.Property.D_LTX));
        for(OrientationPilot.Property p:OrientationPilot.Property.values())
            if(p!=OrientationPilot.Property.D_LTX) assertEquals(before.value(p),model.staged().value(p));
        model.discard(); assertFalse(model.changed()); assertTrue(before.same(model.staged()));
    }
    @Test public void eachCornerAxisUsesItsOppositePairAndOneUnitOnly() {
        for(int corner=0;corner<4;corner++) for(int axis=0;axis<2;axis++) for(int delta:new int[]{-1,1}) {
            ManualCornerModel model=new ManualCornerModel(original(),1000,1000);
            int old=model.value(corner,axis),opponent=corner^(axis==0?1:2);
            int expected=Math.max(0,Math.min(old+delta,1000-model.value(opponent,axis)));
            assertEquals(expected!=old,model.step(corner,axis,delta));assertEquals(expected,model.value(corner,axis));
            int[][] points=model.points();assertEquals(4,points.length);
        }
    }
    @Test public void invalidIndexStepModeLimitsAndOriginalAreRejected() throws Exception {
        ManualCornerModel m=new ManualCornerModel(original(),1000,1000);
        fails(()->m.step(4,0,1));fails(()->m.step(0,2,1));fails(()->m.step(0,0,9));
        fails(()->new ManualCornerModel(original().target(1),1000,1000));
        fails(()->new ManualCornerModel(original(),500,500));
        fails(()->new ManualCornerModel(original().property(OrientationPilot.Property.D_LTX,"-229"),1000,1000));
        fails(()->new ManualCornerModel(original().property(OrientationPilot.Property.D_LTX,"230"),1000,1000));
    }
    @Test public void unsafeCandidateLeavesPreviousStagedStateIntact() throws Exception {
        // Valid paired offsets near the convexity boundary; increasing LB Y makes it concave.
        OrientationPilot.State state=original();
        String[] offsets={"348","429","490","787","688","536","238","206"};
        for(int i=0;i<8;i++) state=state.property(OrientationPilot.Property.values()[3+i],offsets[i]);
        ManualCornerModel model=new ManualCornerModel(state,1000,1000);
        fails(()->model.step(2,1,1));assertTrue(state.same(model.staged()));
    }
    @Test public void limitsAcceptReviewedLenientConfigOnly() throws Exception {
        assertArrayEquals(new int[]{1000,1000},ManualCornerLimits.parse("{\nmanualKeystoneWidth: 1000,\nmanualKeystoneHeight: 1000\n}"));
        assertArrayEquals(new int[]{1000,1000},ManualCornerLimits.parse("{\n\"manualKeystoneWidth\": 1000, // width\n'manualKeystoneHeight':1000,\n}"));
        fails(()->ManualCornerLimits.parse("manualKeystoneWidth: 500\nmanualKeystoneHeight: 1000"));
        fails(()->ManualCornerLimits.parse("manualKeystoneWidth: 1000\nmanualKeystoneWidth: 1000\nmanualKeystoneHeight: 1000"));
        fails(()->ManualCornerLimits.parse("// manualKeystoneWidth: 1000\nmanualKeystoneHeight: 1000"));
        fails(()->ManualCornerLimits.parse("x".repeat(65537)));
    }
    @Test public void largerStepsAndRoundTripPreserveOriginalEncoding(){
        ManualCornerModel model=new ManualCornerModel(original(),1000,1000);
        assertTrue(model.step(0,0,-10));assertEquals(219,model.value(0,0));assertTrue(model.step(0,0,5));assertEquals(224,model.value(0,0));
        assertTrue(model.step(0,0,5));assertFalse(model.changed());assertEquals("+0229",model.staged().value(OrientationPilot.Property.D_LTX));
    }
    @Test public void savedOffsetsLoadWithoutChangingCameraModeOrExactUnchangedValues(){
        ManualCornerModel m=new ManualCornerModel(original(),1000,1000);
        m.loadOffsets(new String[]{"228","559","770","1","538","1","403","399"});
        assertEquals(228,m.value(0,0));assertEquals(original().guard(OrientationPilot.Guard.FOCUS),m.staged().guard(OrientationPilot.Guard.FOCUS));
        m.loadOffsets(original().display());assertFalse(m.changed());
        try{m.loadOffsets(new String[]{"-1","0","0","0","0","0","0","0"});fail();}catch(IllegalArgumentException expected){}
        assertFalse(m.changed());
    }
    @Test public void coarseStepsClampAndLimitsPredictDirections(){
        ManualCornerModel m=new ManualCornerModel(original(),1000,1000);
        assertFalse(m.canMove(0,1));assertTrue(m.canMove(0,0));
        assertFalse(m.canMove(1,0));assertFalse(m.canMove(1,2));
        assertTrue(m.step(0,0,-100));assertEquals(129,m.value(0,0));
        assertTrue(m.step(0,0,-100));assertTrue(m.step(0,0,-50));assertEquals(0,m.value(0,0));
        assertFalse(m.canMove(0,0));assertTrue(m.canMove(0,1));
        assertTrue(m.step(0,0,25));assertEquals(25,m.value(0,0));
    }
    @Test public void clockwiseSelectionAndPhysicalDirections(){
        int c=0;int[] order={1,3,2,0};for(int next:order){c=ManualCornerModel.clockwise(c);assertEquals(next,c);}
        for(int corner=0;corner<4;corner++){
            assertEquals(corner%2==0?-1:1,ManualCornerModel.offsetDirection(corner,0));
            assertEquals(corner<2?-1:1,ManualCornerModel.offsetDirection(corner,2));
        }
    }
    @Test public void resetStagesOnlyDisplayCornersAndPreservesOtherState(){
        ManualCornerModel m=new ManualCornerModel(original(),1000,1000);
        m.loadOffsets(new String[]{"0","0","0","0","0","0","0","0"});
        for(int i=0;i<8;i++)assertEquals(0,m.value(i/2,i%2));
        for(OrientationPilot.Property p:OrientationPilot.Property.values())if(p.ordinal()<3||p.ordinal()>=11)assertEquals(original().value(p),m.staged().value(p));
        m.discard();assertTrue(original().same(m.staged()));
    }
    @Test public void draggingUsesRelativeRasterMovementAndPreservesUnselectedState(){
        OrientationPilot.State start=original();
        OrientationPilot.State target=AlignmentInput.dragTarget(start,0,-10,-5,1920,1080);
        assertEquals("219",target.value(OrientationPilot.Property.D_LTX));assertEquals("555",target.value(OrientationPilot.Property.D_LTY));
        for(OrientationPilot.Property p:OrientationPilot.Property.values())if(p!=OrientationPilot.Property.D_LTX&&p!=OrientationPilot.Property.D_LTY)assertEquals(start.value(p),target.value(p));
        assertTrue(start.same(AlignmentInput.dragTarget(start,0,0,0,1920,1080)));
        assertEquals("+0229",AlignmentInput.dragTarget(start,0,1000,0,1920,1080).value(OrientationPilot.Property.D_LTX));
        assertEquals("0",AlignmentInput.dragTarget(start,0,-10000,0,1920,1080).value(OrientationPilot.Property.D_LTX));
        OrientationPilot.State right=AlignmentInput.dragTarget(start,1,10,5,1920,1080);
        assertEquals("761",right.value(OrientationPilot.Property.D_RTX));assertEquals("5",right.value(OrientationPilot.Property.D_RTY));
    }
    @Test public void rotationPreservesOtherFieldsAndRejectsOutsideRaster(){
        OrientationPilot.State start=original();assertTrue(start.same(AlignmentRotation.rotate(start,0)));
        OrientationPilot.State rotated=AlignmentRotation.rotate(start,.5);
        assertFalse(start.same(rotated));new ManualCornerModel(rotated,1000,1000);
        for(OrientationPilot.Property p:OrientationPilot.Property.values())if(p.ordinal()<3||p.ordinal()>=11)assertEquals(start.value(p),rotated.value(p));
        try{AlignmentRotation.rotate(start,-.5);fail();}catch(IllegalArgumentException expected){}
        try{AlignmentRotation.rotate(start,90);fail();}catch(IllegalArgumentException expected){}
        assertEquals("Desk",AlignmentPresets.name(" Desk "));
        try{AlignmentPresets.name(" ");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void repeatedRotationTargetsUseOneOriginWithoutRoundingDrift(){
        OrientationPilot.State origin=original();
        for(double angle:new double[]{.1,.2,.5,1})new ManualCornerModel(AlignmentRotation.rotate(origin,angle),1000,1000);
        assertTrue(origin.same(AlignmentRotation.rotate(origin,0)));
        try{AlignmentRotation.rotate(origin,16);fail();}catch(IllegalArgumentException expected){}
    }
}
