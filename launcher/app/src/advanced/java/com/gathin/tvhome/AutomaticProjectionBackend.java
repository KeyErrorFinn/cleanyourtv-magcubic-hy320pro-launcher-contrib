package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;

/** Only two fixed property writers and the exact OEM normal correction broadcast. No service binding. */
final class AutomaticProjectionBackend implements AutomaticProjectionControl.Backend {
    private final Context context;private final OrientationVendorBackend vendor=new OrientationVendorBackend();
    AutomaticProjectionBackend(Context context){this.context=context.getApplicationContext();}
    private static String get(String key)throws Exception{return ProjectionScaleBackend.property(key,"");}
    private static void write(String key,String value)throws Exception{
        if(!("persist.sys.tpryauto".equals(key)&&("0".equals(value)||"1".equals(value)))&&!("hotack.sensor.anti_shake".equals(key)&&"1".equals(value)))throw new IllegalArgumentException("Unsupported manufacturer request");
        Class.forName("android.os.SystemProperties").getMethod("set",String.class,String.class).invoke(null,key,value);
    }
    public AutomaticProjectionControl.Snapshot read()throws Exception{
        vendor.resolve();OrientationPilot.State geometry=vendor.read();
        return new AutomaticProjectionControl.Snapshot(geometry,geometry.guard(OrientationPilot.Guard.AUTO),geometry.guard(OrientationPilot.Guard.FOCUS),geometry.guard(OrientationPilot.Guard.CAM),geometry.guard(OrientationPilot.Guard.FOCUS_DIRECTION),get("hotack.sensor.anti_shake"),get("hotack.vaf.runEnable"),get("hotack.vaf.runend"),get("persist.sys.keystonefinalAngle"));
    }
    public boolean conflict(){for(OrientationJournal.Scope scope:OrientationJournal.Scope.values())if(new OrientationJournal(context.getFilesDir(),scope).exists())return true;return false;}
    public void automatic(boolean enabled)throws Exception{write("persist.sys.tpryauto",enabled?"1":"0");}
    public void requestCorrection(){context.sendBroadcast(new Intent("android.intent.hotack_keystone").addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtra("ratio",1).putExtra("keystone",1));}
    public void requestCalibration()throws Exception{write("hotack.sensor.anti_shake","1");}
}
