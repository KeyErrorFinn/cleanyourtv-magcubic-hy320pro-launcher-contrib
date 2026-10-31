package com.gathin.tvhome;

/** Reviewed Front/full-raster no-camera scaling; no broadcast, service or property deletion. */
final class ProjectionScaleBackend implements ProjectionScale.Backend {
    enum Key { STEP("persist.sys.zoom_value"), VECTOR("persist.sys.zoom.value");final String property;Key(String property){this.property=property;} }
    private final OrientationVendorBackend vendor=new OrientationVendorBackend();
    static void manualEnvironment() throws Exception {
        for(String key:new String[]{"hotack.sensor.anti_shake","hotack.vaf.runEnable","hotack.vaf.runend"})if(!"0".equals(property(key,"")))throw new IllegalStateException("The projector is still adjusting. Try again shortly.");
    }
    static void environment() throws Exception {
        
        ProjectionScaleMath.optics(property("persist.sys.DU_LR_V","20.255621"),property("persist.sys.DU_HP_V","11.738539"),property("persist.sys.HOSTPER","0"));
        for(String key:new String[]{"persist.sys.zoom_scale","persist.sys.zoom_scale_old"}){
            String value=property(key,"");if(!value.isEmpty()&&!"0".equals(value))throw new IllegalStateException("Direct scaling currently requires the full-raster aspect");
        }
        for(String key:new String[]{"hotack.sensor.anti_shake","hotack.vaf.runEnable","hotack.vaf.runend"})
            if(!"0".equals(property(key,"")))throw new IllegalStateException("Wait for motor/correction activity to finish; a saved automatic angle is not supported here");
        if(!"45.0".equals(property("persist.sys.keystonefinalAngle","")))throw new IllegalStateException("Saved angle differs from this installation's reviewed 45-degree profile; no command sent");
    }
    static String property(String key,String fallback)throws Exception{return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,key,fallback);}
    public void resolve() throws Exception{vendor.resolve();environment();}
    public void prepareWrite() throws Exception{environment();vendor.prepareWrite();}
    public OrientationPilot.State read() throws Exception{environment();return vendor.read();}
    public int[] driver() throws Exception{return DriverKeystoneState.read();}
    public int coordinates(int[][] points) throws Exception{prepareWrite();return vendor.coordinates(points);}
    public void persist(OrientationPilot.Property key,String value) throws Exception{
        if(key.ordinal()<3||key.ordinal()>=11)throw new IllegalArgumentException("Scaling can only write displayed corners");prepareWrite();vendor.persist(key,value);
    }
    public void persistZoom(Key key,String value) throws Exception{
        prepareWrite();writeZoom(key,value);
    }
    static void writeZoom(Key key,String value) throws Exception{
        if(key==Key.STEP)ProjectionScaleMath.step(value);else ProjectionScaleMath.vectorEncoding(value);
        Class.forName("android.os.SystemProperties").getMethod("set",String.class,String.class).invoke(null,key.property,value);
    }
}
