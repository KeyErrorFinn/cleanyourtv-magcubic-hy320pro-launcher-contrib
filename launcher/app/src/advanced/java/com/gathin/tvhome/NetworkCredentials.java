package com.gathin.tvhome;
import java.nio.charset.StandardCharsets;
/** Validates the selected security scheme; never silently downgrades encryption or certificate checks. */
final class NetworkCredentials {
 static void validate(String name,String password,String security,String identity,String domain){
  SettingsSessionPolicy.network(name,"",false);int bytes=password==null?0:password.getBytes(StandardCharsets.UTF_8).length;
  switch(security){
   case "OPEN":case "OWE":return;
   case "WPA2":if(hex(password,64)||bytes>=8&&bytes<=63)return;throw new IllegalArgumentException("WPA2 needs 8-63 bytes or a 64-digit hexadecimal key.");
   case "WPA3":if(bytes>=1&&bytes<=63)return;throw new IllegalArgumentException("WPA3 needs a password of 1-63 bytes.");
   case "WEP":if(hex(password,10)||hex(password,26)||hex(password,58)||ascii(password)&&(bytes==5||bytes==13||bytes==29))return;throw new IllegalArgumentException("WEP needs 5, 13 or 29 ASCII characters, or 10, 26 or 58 hexadecimal digits.");
   case "PEAP":case "TTLS":if(identity==null||identity.trim().isEmpty()||identity.length()>256||bytes<1||bytes>256)throw new IllegalArgumentException("Enter the enterprise identity and password.");domain(domain);return;
   default:throw new IllegalArgumentException("Unsupported network security.");
  }
 }
 static boolean hex(String value,int length){return value!=null&&value.length()==length&&value.matches("[0-9a-fA-F]+");}
 static boolean ascii(String value){if(value==null)return false;for(char c:value.toCharArray())if(c<32||c>126)return false;return true;}
 static String domain(String value){if(value==null||value.isEmpty()||!value.equals(value.trim()))throw new IllegalArgumentException("Enter the authentication server's domain.");String ascii;try{ascii=java.net.IDN.toASCII(value,java.net.IDN.USE_STD3_ASCII_RULES);}catch(IllegalArgumentException e){throw new IllegalArgumentException("Enter a valid authentication-server domain.");}if(ascii.length()>253||ascii.startsWith(".")||ascii.endsWith("."))throw new IllegalArgumentException("Enter a valid authentication-server domain.");for(String label:ascii.split("[.]",-1))if(label.isEmpty()||label.length()>63)throw new IllegalArgumentException("Enter a valid authentication-server domain.");return ascii;}
 private NetworkCredentials(){}
}
