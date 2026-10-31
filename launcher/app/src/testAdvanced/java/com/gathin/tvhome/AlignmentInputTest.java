package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class AlignmentInputTest {
 @Test public void heldAndRapidClickAccelerationAreBoundedAndReset(){
  assertEquals(1,AlignmentInput.heldStep(0));assertEquals(8,AlignmentInput.heldStep(99));
  AlignmentInput i=new AlignmentInput();for(int n=1;n<=12;n++)assertEquals(Math.min(8,n),i.tapStep(n*40));
  assertEquals(1,i.tapStep(2000));assertEquals(1,i.tapStep(2600));assertEquals(1,i.tapStep(20));
 }
 @Test public void cornerAndArrowHitTargetsMatchFullScreenPattern(){
  for(int corner=0;corner<4;corner++){
   float x=corner%2==0?96:1824,y=corner<2?96:984;
   assertEquals(corner,AlignmentInput.cornerAt(x,y,1920,1080,1));
   assertEquals(0,AlignmentInput.arrowAt(corner,x-60,y,1920,1080,1));
   assertEquals(1,AlignmentInput.arrowAt(corner,x+60,y,1920,1080,1));
   assertEquals(2,AlignmentInput.arrowAt(corner,x,y-60,1920,1080,1));
   assertEquals(3,AlignmentInput.arrowAt(corner,x,y+60,1920,1080,1));
  }
  assertEquals(-1,AlignmentInput.cornerAt(960,540,1920,1080,1));
  assertEquals(-1,AlignmentInput.arrowAt(0,96,96,1920,1080,1));
 }
 @Test public void stepButtonsAreBounded(){
  assertEquals(1,AlignmentInput.adjacentStep(0,1));assertEquals(0,AlignmentInput.adjacentStep(1,-1));
  assertEquals(100,AlignmentInput.adjacentStep(100,1));assertEquals(0,AlignmentInput.adjacentStep(0,-1));
 }
}
