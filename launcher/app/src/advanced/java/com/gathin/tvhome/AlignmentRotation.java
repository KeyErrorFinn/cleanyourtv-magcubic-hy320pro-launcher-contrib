package com.gathin.tvhome;
/** Small-angle image rotation through the same four validated corner coordinates. */
final class AlignmentRotation {
 static OrientationPilot.State rotate(OrientationPilot.State state,double degrees){
  if(!Double.isFinite(degrees)||Math.abs(degrees)>15)throw new IllegalArgumentException("Unsupported rotation increment");
  ManualCornerModel m=new ManualCornerModel(state,1000,1000);if(degrees==0)return state;
  int[][] points=m.points();double cx=0,cy=0;for(int[] p:points){cx+=p[0]/4.0;cy+=p[1]/4.0;}
  double a=Math.toRadians(degrees),cos=Math.cos(a),sin=Math.sin(a);String[] offsets=state.display().clone();
  for(int i=0;i<4;i++){
   int x=(int)Math.round(cx+(points[i][0]-cx)*cos-(points[i][1]-cy)*sin);
   int y=(int)Math.round(cy+(points[i][0]-cx)*sin+(points[i][1]-cy)*cos);
   if(x<0||x>1919||y<0||y>1079)throw new IllegalArgumentException("Rotation reaches the projection boundary");
   offsets[i*2]=Integer.toString(i%2==0?x:1919-x);offsets[i*2+1]=Integer.toString(i<2?y:1079-y);
  }
  m.loadOffsets(offsets);return m.staged();
 }
}
