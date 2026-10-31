package com.gathin.tvhome;
import android.os.*;import java.util.*;
/** Paired-computer management through Android's actual authorised debugging service. */
@android.annotation.TargetApi(34)
final class ShellDebugSession {
 private final Object adb;private final Runnable stopOwner;
 ShellDebugSession(Runnable stopOwner)throws Exception{this.stopOwner=stopOwner;IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"adb");adb=Class.forName("android.debug.IAdbManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);}
 private Object[] pairs()throws Exception{Object[] devices=(Object[])adb.getClass().getMethod("getPairedDevices").invoke(adb);if(devices==null)throw new IllegalStateException("Paired computers unavailable");return devices;}
 private String fingerprint(Object pair)throws Exception{Object device=pair.getClass().getField("device").get(pair);String value=(String)device.getClass().getField("guid").get(device);if(!SettingsSessionPolicy.fingerprint(value))throw new IllegalStateException("Unrecognised paired-computer identity");return value;}
 Bundle run(int operation,Bundle input)throws Exception{
  Bundle out=new Bundle();
  if(operation==SettingsSessionClient.ADB_READ){ArrayList<Bundle> items=new ArrayList<>();for(Object pair:pairs()){Object device=pair.getClass().getField("device").get(pair);Bundle item=new Bundle();item.putString("key",fingerprint(pair));item.putString("name",(String)device.getClass().getField("name").get(device));item.putBoolean("connected",device.getClass().getField("connected").getBoolean(device));items.add(item);}out.putParcelableArrayList("devices",items);out.putInt("port",(Integer)adb.getClass().getMethod("getAdbWirelessPort").invoke(adb));return out;}
  if(operation==SettingsSessionClient.ADB_UNPAIR){String key=input.getString("key");boolean found=false;for(Object pair:pairs())if(Objects.equals(key,fingerprint(pair)))found=true;if(!found)throw new IllegalStateException("Paired computers changed. Reopen this page.");adb.getClass().getMethod("unpairDevice",String.class).invoke(adb,key);out.putBoolean("requested",true);new Handler(Looper.getMainLooper()).postDelayed(stopOwner,250);return out;}
  if(operation==SettingsSessionClient.ADB_CLEAR){adb.getClass().getMethod("clearDebuggingKeys").invoke(adb);out.putBoolean("requested",true);new Handler(Looper.getMainLooper()).postDelayed(stopOwner,250);return out;}
  throw new IllegalArgumentException("Unsupported debugging action");
 }
}
