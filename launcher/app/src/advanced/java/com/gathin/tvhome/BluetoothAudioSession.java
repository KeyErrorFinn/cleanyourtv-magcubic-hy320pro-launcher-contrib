package com.gathin.tvhome;

import android.bluetooth.*;
import android.content.*;
import android.os.ParcelUuid;
import java.lang.reflect.Method;
import java.util.*;
import java.io.*;

/** Foreground A2DP proxy. Never touches Bluetooth power, input profiles, alias or connection policy. */
final class BluetoothAudioSession implements AutoCloseable {
    enum Action { CONNECT, DISCONNECT, FORGET }
    static final class Device {
        final BluetoothDevice target;final String label;final boolean audio,aclKnown,aclConnected;final int state;
        Device(BluetoothDevice target,String label,boolean audio,boolean aclKnown,boolean aclConnected,int state){this.target=target;this.label=label;this.audio=audio;this.aclKnown=aclKnown;this.aclConnected=aclConnected;this.state=state;}
        boolean forget(){return BluetoothAudioPolicy.forget(audio,aclKnown,aclConnected,state);}
    }
    private final Context context;private final BluetoothAdapter adapter;private final Runnable changed;
    private volatile BluetoothProfile profile;private volatile boolean closed;
    private boolean registered;private final Method connect,disconnect,remove,acl;
    BluetoothAudioSession(Context context,Runnable changed){
        if(!"Projector_20260713.153921".equals(android.os.Build.ID)||android.os.Build.VERSION.SDK_INT!=34||android.os.Process.myUid()/100000!=0)throw new IllegalStateException("Reviewed projector firmware/user required");
        this.context=context.getApplicationContext();this.changed=changed;
        BluetoothManager manager=(BluetoothManager)context.getSystemService(Context.BLUETOOTH_SERVICE);adapter=manager==null?null:manager.getAdapter();
        connect=method(BluetoothA2dp.class,"connect",BluetoothDevice.class);disconnect=method(BluetoothA2dp.class,"disconnect",BluetoothDevice.class);
        remove=method(BluetoothDevice.class,"removeBond");acl=method(BluetoothDevice.class,"isConnected");
    }
    private static Method method(Class<?> owner,String name,Class<?>...arguments){try{return owner.getMethod(name,arguments);}catch(ReflectiveOperationException|SecurityException unavailable){return null;}}
    private final BluetoothProfile.ServiceListener listener=new BluetoothProfile.ServiceListener(){
        public void onServiceConnected(int type,BluetoothProfile value){if(type!=BluetoothProfile.A2DP)return;if(closed){if(adapter!=null)adapter.closeProfileProxy(type,value);return;}profile=value;changed.run();}
        public void onServiceDisconnected(int type){if(type==BluetoothProfile.A2DP){profile=null;if(!closed)changed.run();}}
    };
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent ignored){if(!closed)changed.run();}};
    void open(){
        if(context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("Nearby devices access is required");
        if(adapter==null)throw new IllegalStateException("Bluetooth is unavailable");
        IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        if(android.os.Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,android.Manifest.permission.BLUETOOTH_CONNECT,null,Context.RECEIVER_EXPORTED);
        else context.registerReceiver(receiver,filter,android.Manifest.permission.BLUETOOTH_CONNECT,null);
        registered=true;
        if(!adapter.getProfileProxy(context,listener,BluetoothProfile.A2DP))throw new IllegalStateException("Media-audio service is unavailable");
    }
    List<Device> devices(){
        if(context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("Nearby devices access is required");
        if(closed||adapter==null)throw new IllegalStateException("Bluetooth page is closed");
        List<Device> result=new ArrayList<>();for(BluetoothDevice device:adapter.getBondedDevices())result.add(inspect(device));
        result.sort((a,b)->a.label.compareToIgnoreCase(b.label));return result;
    }
    Device inspect(BluetoothDevice device){
        if(context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("Nearby devices access is required");
        if(closed||adapter==null||!adapter.getBondedDevices().contains(device))throw new IllegalStateException("Accessory is no longer paired");
        String name=device.getName();if(name==null||name.trim().isEmpty())name="Paired accessory";
        Set<UUID> services=new HashSet<>();ParcelUuid[] uuids=device.getUuids();if(uuids!=null)for(ParcelUuid uuid:uuids)if(uuid!=null)services.add(uuid.getUuid());
        BluetoothClass type=device.getBluetoothClass();boolean audio=BluetoothAudioPolicy.audio(device.getBondState()==BluetoothDevice.BOND_BONDED,device.getType(),type==null?-1:type.getMajorDeviceClass(),services);
        boolean known=false,connected=false;
        if(acl!=null)try{Object value=acl.invoke(device);if(value instanceof Boolean){known=true;connected=(Boolean)value;}}catch(ReflectiveOperationException|RuntimeException unavailable){/* Unknown stays protected from Forget. */}
        BluetoothProfile current=profile;int state=current==null?-1:current.getConnectionState(device);
        return new Device(device,name,audio,known,connected,state);
    }
    String serviceStatus(){return profile==null?"Media-audio service not connected":connect==null||disconnect==null||remove==null||acl==null?"Media-audio service connected; some actions are unavailable on this firmware":"Media-audio service connected";}
    boolean ready(Action action){return action==Action.FORGET?remove!=null:profile!=null&&(action==Action.CONNECT?connect:disconnect)!=null;}
    void request(Action action,BluetoothDevice target)throws Exception{
        if(context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("Nearby devices access is required");
        if(closed||adapter==null||!adapter.isEnabled())throw new IllegalStateException("Bluetooth is not ready");
        Device current=inspect(target);if(!current.audio)throw new SecurityException("Remote, input and unidentified accessories are protected here");
        if(!ready(action))throw new IllegalStateException("This firmware has not exposed that media-audio action");
        if(action==Action.FORGET&&!current.forget())throw new IllegalStateException("Power off the audio accessory first. A verified disconnected accessory is required to forget pairing");
        if(action==Action.CONNECT&&current.state!=BluetoothProfile.STATE_DISCONNECTED)throw new IllegalStateException("Refresh the current media-audio connection first");
        if(action==Action.DISCONNECT&&current.state!=BluetoothProfile.STATE_CONNECTED)throw new IllegalStateException("Media audio is not connected");
        record(action,"BEFORE_REQUEST",target);
        try{
            Object result=action==Action.FORGET?remove.invoke(target):(action==Action.CONNECT?connect:disconnect).invoke(profile,target);
            if(!Boolean.TRUE.equals(result))throw new IllegalStateException("Android did not accept the request");
            record(action,"REQUEST_ACCEPTED_NOT_COMPLETION",target);
        }catch(Exception failure){try{record(action,"NOT_CONFIRMED_NO_RETRY",target);}catch(Exception logging){failure.addSuppressed(logging);}throw failure;}
    }
    private synchronized void record(Action action,String event,BluetoothDevice device)throws Exception{
        // Hash only: no address, device name or pairing credential in the action log.
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(device.getAddress().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        StringBuilder id=new StringBuilder();for(byte b:digest)id.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        try(FileOutputStream file=new FileOutputStream(new File(context.getFilesDir(),"bluetooth-audio-actions.bin"),true);DataOutputStream out=new DataOutputStream(file)){
            out.writeLong(System.currentTimeMillis());out.writeUTF(action.name());out.writeUTF(event);out.writeUTF(id.toString());out.flush();file.getFD().sync();
        }
    }
    public void close(){closed=true;if(registered){try{context.unregisterReceiver(receiver);}catch(IllegalArgumentException ignored){}registered=false;}BluetoothProfile current=profile;profile=null;if(adapter!=null&&current!=null)try{adapter.closeProfileProxy(BluetoothProfile.A2DP,current);}catch(SecurityException ignored){}}
}
