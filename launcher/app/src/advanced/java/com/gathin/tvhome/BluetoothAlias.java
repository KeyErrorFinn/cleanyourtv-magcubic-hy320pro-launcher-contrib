package com.gathin.tvhome;
import android.bluetooth.BluetoothDevice;import android.content.AttributionSource;
/** Raw alias read avoids BluetoothDevice.getAlias()'s name fallback and preserves absence. */
@android.annotation.TargetApi(34)
final class BluetoothAlias {
 static String read(BluetoothDevice device,AttributionSource source)throws Exception{
  java.lang.reflect.Method getter=BluetoothDevice.class.getDeclaredMethod("getService");getter.setAccessible(true);Object service=getter.invoke(null);if(service==null)throw new IllegalStateException("Bluetooth service unavailable");Class<?> receiverType=Class.forName("com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver");Object receiver=receiverType.getMethod("get").invoke(null);Class.forName("android.bluetooth.IBluetooth").getMethod("getRemoteAlias",BluetoothDevice.class,AttributionSource.class,receiverType).invoke(service,device,source,receiver);Object result=receiverType.getMethod("awaitResultNoInterrupt",java.time.Duration.class).invoke(receiver,java.time.Duration.ofSeconds(3));Object alias=result.getClass().getMethod("getValue",Object.class).invoke(result,new Object[]{null});if(alias!=null&&!(alias instanceof String))throw new IllegalStateException("Unrecognised alias state");return (String)alias;
 }
 private BluetoothAlias(){}
}
