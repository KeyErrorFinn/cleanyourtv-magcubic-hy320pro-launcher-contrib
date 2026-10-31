package com.gathin.tvhome;

/** Fixed reset transport. No camera/service starts, zoom setters or calibration writes. */
final class ProjectionResetBackend implements ProjectionReset.Backend {
    private final OrientationVendorBackend vendor=new OrientationVendorBackend();
    private void environment() throws Exception {
        ProjectionScaleBackend.environment();
        for(String key:new String[]{"persist.sys.zoom_scale","persist.sys.zoom_scale_old"}) {
            String value=ProjectionScaleBackend.property(key,"");
            if(!value.isEmpty()&&!"0".equals(value))throw new IllegalStateException("Reset currently supports the full-raster aspect only. No picture changed.");
        }
        for(String key:new String[]{"hotack.sensor.anti_shake","hotack.vaf.runEnable"})
            if(!"0".equals(ProjectionScaleBackend.property(key,"")))throw new IllegalStateException("Motor/correction activity or an existing automatic angle prevents this reset. Nothing further was changed.");
    }
    public void resolve() throws Exception{vendor.resolve();environment();}
    public void prepareWrite() throws Exception{environment();vendor.prepareWrite();}
    public OrientationPilot.State read() throws Exception{environment();return vendor.read();}
    public int[] driver() throws Exception{return DriverKeystoneState.read();}
    public int coordinates(int[][] points) throws Exception{prepareWrite();return vendor.coordinates(points);}
    public void persist(OrientationPilot.Property key,String value) throws Exception {
        if(key.ordinal()<3||key.ordinal()>=19)throw new IllegalArgumentException("Reset can only write the two corner models");
        prepareWrite();vendor.persist(key,value);
    }
    public void persistZoom(ProjectionScaleBackend.Key key,String value)throws Exception{prepareWrite();ProjectionScaleBackend.writeZoom(key,value);}
}
