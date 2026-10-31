package com.gathin.tvhome;
import android.os.*;
final class SettingsSessionClient {
 static final String DESCRIPTOR="com.gathin.tvhome.SettingsSession.v1";
 static final int STATUS=1,WIFI=2,BLUETOOTH=3,TIME=4,ZONE=5,NETWORKS=6,CONNECT=7,FORGET=8,ADD=9,CACHE=10,FORCE_STOP=11,DATA=12,PERMISSION=13,SET_PERMISSION=14,IDENTIFIERS=15,STOP=16,TIMER=17,RESTORE_PERMISSION=18,HOTSPOT_READ=19,HOTSPOT_SET=20,HOTSPOT_POWER=21,HOTSPOT_RESTORE=22,BONDED=23,DEVICE=24,ETHERNET_READ=25,ETHERNET_SET=26,ETHERNET_RESTORE=27,ADB_READ=28,ADB_UNPAIR=29,ADB_CLEAR=30,PAIR_START=31,PAIR_STOP=32,NETWORK_FEATURES=33,SESSION_MODE=34;
 private static volatile IBinder service;
 private static volatile boolean independent;
 static boolean independentOfDebugging(){return connected()&&independent;}
 static void independentOfDebugging(boolean allowed){independent=allowed;}
 static synchronized void attach(IBinder binder)throws RemoteException{if(binder==null||!DESCRIPTOR.equals(binder.getInterfaceDescriptor()))throw new SecurityException("Unknown helper");if(service!=null&&service.isBinderAlive())throw new SecurityException("Stop the existing helper first");service=binder;binder.linkToDeath(()->{synchronized(SettingsSessionClient.class){if(service==binder){service=null;independent=false;}}},0);}
 static boolean connected(){IBinder b=service;return b!=null&&b.isBinderAlive();}
 static Bundle call(int code,Bundle input)throws Exception{
  IBinder b=service;if(b==null||!b.isBinderAlive())throw new IllegalStateException("ADB helper is not connected. Start it from your computer.");
  Parcel request=Parcel.obtain(),reply=Parcel.obtain();try{request.writeInterfaceToken(DESCRIPTOR);request.writeBundle(input==null?new Bundle():input);if(!b.transact(code,request,reply,0))throw new IllegalStateException("Unsupported helper operation");reply.readException();Bundle result=reply.readBundle(SettingsSessionClient.class.getClassLoader());if(result==null)throw new IllegalStateException("No helper response");return result;}finally{request.recycle();reply.recycle();}
 }
 static Bundle input(String key,String value){Bundle b=new Bundle();b.putString(key,value);return b;}
 private SettingsSessionClient(){}
}
