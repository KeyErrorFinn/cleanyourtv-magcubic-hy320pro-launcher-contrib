package com.gathin.tvhome;

/** Exact stock no-camera/full-raster branch: OEM steps0..20, not a percentage guess. */
final class ProjectionScaleMath {
    static int step(String encoded){int step=Integer.parseInt(encoded);if(step<0||step>20||!Integer.toString(step).equals(encoded))throw new IllegalArgumentException("Unsupported scaling step");return step;}
    private static final double LENS_H=26.103255,LENS_V=15.472923;
    static void optics(String horizontal,String vertical,String host){
        if(Double.parseDouble(horizontal)!=LENS_H||Double.parseDouble(vertical)!=LENS_V||Double.parseDouble(host)!=0d)
            throw new IllegalArgumentException("Optical profile differs from the reviewed scaling profile");
    }
    static String vector(int step){step(Integer.toString(step));String x=Integer.toString(8*step),y=Integer.toString(9*step/2);return String.join(",",x,y,x,y,x,y,x,y);}
    /** Independent zero-angle reconstruction, including stock physical-aspect fitting and truncation. */
    static String[] offsets(int step){return offsets(step,0d);}
    static String[] offsets(int step,double angle){
        step(Integer.toString(step));if(!Double.isFinite(angle)||Math.abs(angle)>45d)throw new IllegalArgumentException("Unsupported saved angle");
        int x=8*step,y=9*step/2;double ratio=(double)(1080-2*y)/(1920-2*x);
        double lensX=Math.tan(LENS_H*Math.PI/180d)*1500d,lensY=Math.tan(LENS_V*Math.PI/180d)*1500d;
        double left=((x-960)*lensX)/960d,right=((960-x)*lensX)/960d;
        double top=((540-y)*lensY)/540d,bottom=((y-540)*lensY)/540d;
        double[] px={left,right,left,right},py={top,top,bottom,bottom},pz=new double[4];
        double radians=angle*Math.PI/180d,sin=Math.sin(radians),cos=Math.cos(radians);
        for(int i=0;i<4;i++){
            double ry=cos*(-py[i])+(-sin)*1500d,rz=sin*(-py[i])+cos*1500d,factor=-1500d/rz;
            px[i]=(-px[i])*factor;py[i]=ry*factor;pz[i]=1500d+rz*factor;
        }
        if(px[1]>=px[3]){double height=(px[3]-px[2])*ratio;px[0]=px[2];py[0]=py[2]+height;px[1]=px[3];py[1]=py[3]+height;}
        else{double height=(px[1]-px[0])*ratio;px[2]=px[0];py[2]=py[0]-height;px[3]=px[1];py[3]=py[1]-height;}
        sin=Math.sin(-radians);cos=Math.cos(-radians);int[] raster=new int[8];
        for(int i=0;i<4;i++){
            double depth=1500d-pz[i],ry=cos*(-py[i])+(-sin)*depth,rz=sin*(-py[i])+cos*depth,factor=-1500d/rz;
            raster[2*i]=pixel((-px[i])*factor,lensX,1920);raster[2*i+1]=pixel(ry*factor,lensY,1080);
        }
        return new String[]{Integer.toString(raster[0]),Integer.toString(1079-raster[1]),Integer.toString(1919-raster[2]),Integer.toString(1079-raster[3]),Integer.toString(raster[4]),Integer.toString(raster[5]),Integer.toString(1919-raster[6]),Integer.toString(raster[7])};
    }
    private static int pixel(double position,double halfExtent,int extent){return Math.max(0,Math.min(extent-1,(int)(((position+halfExtent)*extent)/(halfExtent*2d))));}
    static void vectorEncoding(String vector){for(int step=0;step<=20;step++)if(vector(step).equals(vector))return;throw new IllegalArgumentException("Unreviewed zoom vector");}
    static OrientationPilot.State target(OrientationPilot.State original,int step){
        original.validateScaled(true,true);String[] offsets=OrientationMath.permute(offsets(step,45d),0,original.mode());OrientationPilot.State target=original;
        for(int i=0;i<8;i++){OrientationPilot.Property p=OrientationPilot.Property.values()[3+i];target=target.property(p,Integer.parseInt(original.value(p))==Integer.parseInt(offsets[i])?original.value(p):offsets[i]);}
        return target.zoomStep(Integer.toString(step)).zoomVector(vector(step));
    }
    private ProjectionScaleMath() { }
}
