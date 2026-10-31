package com.gathin.tvhome;
import android.content.*;import android.database.Cursor;import android.net.Uri;import android.os.*;
/** Registration is shell-only and additionally protected by DUMP; no network endpoint. */
public final class SettingsSessionProvider extends ContentProvider {
 private static final IBinder ownerLease=new Binder();
 public boolean onCreate(){
  getContext().getContentResolver().registerContentObserver(android.provider.Settings.Global.getUriFor("adb_wifi_enabled"),false,new android.database.ContentObserver(new Handler(Looper.getMainLooper())){
   @Override public void onChange(boolean self){if(android.provider.Settings.Global.getInt(getContext().getContentResolver(),"adb_wifi_enabled",0)==0&&SettingsSessionClient.connected()&&!SettingsSessionClient.independentOfDebugging())new Thread(()->{try{SettingsSessionClient.call(SettingsSessionClient.STOP,new Bundle());}catch(Exception ignored){/* A disconnected or already-stopping helper cannot accept settings writes. */}},"settings-session-close").start();}
  });return true;
 }
 @Override public Bundle call(String method,String arg,Bundle extras){
  if(Binder.getCallingUid()!=2000||!"Projector_20260713.153921".equals(Build.ID)||android.os.Process.myUid()/100000!=0)throw new SecurityException("Authorised projector shell required");
  Bundle out=new Bundle();try{
   if("attach".equals(method)){if(extras==null)throw new IllegalArgumentException("Missing helper");SettingsSessionClient.attach(extras.getBinder("service"));boolean independent=StarterStatusProvider.HANDSHAKE.attached(extras.getString("starter_nonce"),SystemClock.elapsedRealtime());SettingsSessionClient.independentOfDebugging(independent);out.putBoolean("independent",independent);out.putBoolean("accepted",true);out.putBinder("owner",ownerLease);}
   else if("session_mode".equals(method)){out.putBoolean("until_debug_off",getContext().getSharedPreferences("settings_helper_policy",0).getBoolean("until_debug_off",false));}
   else if("status".equals(method)){out.putBoolean("connected",SettingsSessionClient.connected());}
   else if("capabilities".equals(method)){return SettingsSessionClient.call(SettingsSessionClient.STATUS,new Bundle());}
   else if("receipt".equals(method)){android.content.SharedPreferences p=getContext().getSharedPreferences("settings_session_receipt",0);out.putString("cache_error",p.getString("cache_error",null));out.putInt("operation",p.getInt("operation",0));out.putBoolean("success",p.getBoolean("success",false));out.putLong("at",p.getLong("at",0));out.putString("package",p.getString("package",null));}
   else if("originals".equals(method)){return SessionSettingsUi.originals(getContext());}
   else if("restore".equals(method)){return SessionSettingsUi.restore(getContext(),arg);}
   else if("stop".equals(method)){SettingsSessionClient.call(SettingsSessionClient.STOP,new Bundle());out.putBoolean("stopped",true);}
   else throw new IllegalArgumentException("Unsupported method");
  }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("Helper connection failed",e);}return out;
 }
 public Cursor query(Uri u,String[]p,String s,String[]a,String o){throw new UnsupportedOperationException();}
 public String getType(Uri u){return "vnd.android.cursor.item/vnd.cleanyourtv.settings-session";}
 public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
 public int delete(Uri u,String s,String[]a){throw new UnsupportedOperationException();}
 public int update(Uri u,ContentValues v,String s,String[]a){throw new UnsupportedOperationException();}
}
