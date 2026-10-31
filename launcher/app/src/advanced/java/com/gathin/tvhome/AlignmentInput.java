package com.gathin.tvhome;

/** Pure input geometry/acceleration shared by the wall-alignment view and JVM checks. */
final class AlignmentInput {
 private long lastTap=-1;
 private int taps;
 static int heldStep(int repeat){return Math.max(1,Math.min(8,repeat+1));}
 int tapStep(long now){taps=lastTap>=0 && now>=lastTap && now-lastTap<600?Math.min(8,taps+1):1;lastTap=now;return taps;}
 static int cornerAt(float x,float y,float width,float height,float unit){
  for(int i=0;i<4;i++){float cx=i%2==0?96*unit:width-96*unit,cy=i<2?96*unit:height-96*unit;
   if(Math.abs(x-cx)<=28*unit && Math.abs(y-cy)<=28*unit)return i;}
  return -1;
 }
 static int arrowAt(int corner,float x,float y,float width,float height,float unit){
  float cx=corner%2==0?96*unit:width-96*unit,cy=corner<2?96*unit:height-96*unit;
  float dx=(x-cx)/unit,dy=(y-cy)/unit;
  if(Math.abs(dy)<=23 && dx>=32 && dx<=90)return 1;
  if(Math.abs(dy)<=23 && dx<=-32 && dx>=-90)return 0;
  if(Math.abs(dx)<=23 && dy>=32 && dy<=90)return 3;
  if(Math.abs(dx)<=23 && dy<=-32 && dy>=-90)return 2;
  return -1;
 }
 static int adjacentStep(int current,int direction){
  int[] values={0,1,5,10,25,50,100};for(int i=0;i<values.length;i++)if(values[i]==current)return values[Math.max(0,Math.min(values.length-1,i+direction))];
  throw new IllegalArgumentException("Unknown step");
 }
 static OrientationPilot.State dragTarget(OrientationPilot.State start,int corner,float dx,float dy,float width,float height){
  if(!Float.isFinite(dx)||!Float.isFinite(dy)||width<=0||height<=0)throw new IllegalArgumentException("Invalid drag");
  ManualCornerModel m=new ManualCornerModel(start,1000,1000);String[] offsets=start.display().clone();
  for(int axis=0;axis<2;axis++){
   int opponent=corner^(axis==0?1:2),max=1000-m.value(opponent,axis);
   float pixels=axis==0?dx*1920/width:dy*1080/height;
   int delta=Math.round(Math.max(-1000,Math.min(1000,pixels)))*ManualCornerModel.offsetDirection(corner,axis==0?1:3);
   int value=Math.max(0,Math.min(max,m.value(corner,axis)+delta));
   if(value!=m.value(corner,axis))offsets[corner*2+axis]=Integer.toString(value);
  }
  m.loadOffsets(offsets);return m.staged();
 }
}
