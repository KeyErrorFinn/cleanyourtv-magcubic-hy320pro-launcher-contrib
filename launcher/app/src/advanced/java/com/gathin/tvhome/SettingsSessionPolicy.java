package com.gathin.tvhome;
import java.util.*;
final class SettingsSessionPolicy {
 static final long LIFETIME=2*60*60*1000L;
 static void caller(int actual,int owner){if(actual!=owner||owner<10000)throw new SecurityException("Unauthorised settings client");}
 static void live(long now,long started,boolean debugging){if(!debugging||now<started||now-started>=LIFETIME)throw new SecurityException("ADB helper is not connected. Start it from your computer.");}
 static void userPackage(String name,boolean system){if(name==null||system||name.equals("com.gathin.tvhome")||name.equals("android")||name.equals("com.android.vending")||name.equals("com.google.android.gms")||name.equals("com.google.android.gsf")||name.startsWith("com.android.")||name.startsWith("com.htc.")||name.startsWith("com.softwinner.")||name.startsWith("com.hysd.")||name.startsWith("com.hy."))throw new SecurityException("Projector, core and preinstalled components are protected");}
 static String[] permissionRecord(String key){if(key==null||!key.startsWith("permission."))throw new IllegalArgumentException("Invalid permission record");int split=key.indexOf('|',11);if(split<=11||split>=key.length()-1||split!=key.lastIndexOf('|'))throw new IllegalArgumentException("Invalid permission record");return new String[]{key.substring(11,split),key.substring(split+1)};}
 static boolean fingerprint(String value){return value!=null&&value.matches("([0-9A-Fa-f]{2}:){15}[0-9A-Fa-f]{2}");}
 static void alias(String value){if(value!=null&&(value.trim().isEmpty()||value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>248||value.chars().anyMatch(c->c<32||c==127)))throw new IllegalArgumentException("Choose a device name of up to 248 bytes, without control characters.");}
 static byte[] ipv4(String text){if(text==null)throw new IllegalArgumentException("Enter an IPv4 address");String[] parts=text.split("\\.",-1);if(parts.length!=4)throw new IllegalArgumentException("Enter an IPv4 address");byte[] bytes=new byte[4];for(int i=0;i<4;i++){if(!parts[i].matches("[0-9]{1,3}"))throw new IllegalArgumentException("Enter an IPv4 address");int value=Integer.parseInt(parts[i]);if(value>255)throw new IllegalArgumentException("Enter an IPv4 address");bytes[i]=(byte)value;}return bytes;}
 static void timestamp(long value){if(value<946684800000L||value>2145916799999L)throw new IllegalArgumentException("Choose a date between 2000 and 2037.");}
 static void zone(String id){if(id==null||!new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs())).contains(id))throw new IllegalArgumentException("Unknown time zone");}
 static void timer(int seconds){if(seconds!=0&&seconds!=300&&seconds!=600&&seconds!=1200&&seconds!=3600)throw new IllegalArgumentException("Unknown shutdown timer");}
 static String quoted(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
 static void network(String name,String password,boolean secure){if(name==null||name.isEmpty()||name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32||name.indexOf('\0')>=0)throw new IllegalArgumentException("Enter a network name of up to 32 bytes.");if(secure&&(password==null||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<8||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>63))throw new IllegalArgumentException("WPA2 password must be 8 to 63 bytes.");}
 private SettingsSessionPolicy(){}
}
