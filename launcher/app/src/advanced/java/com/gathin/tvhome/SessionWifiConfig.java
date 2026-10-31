package com.gathin.tvhome;
import android.net.wifi.*;import android.os.Bundle;
@android.annotation.TargetApi(34)
final class SessionWifiConfig {
 static WifiConfiguration create(Bundle input,WifiManager wifi)throws Exception{
  String name=input.getString("name"),password=input.getString("password",""),security=input.getString("security",input.getBoolean("secure")?"WPA2":"OPEN");
  NetworkCredentials.validate(name,password,security,input.getString("identity"),input.getString("domain"));
  WifiConfiguration c=new WifiConfiguration();c.SSID=SettingsSessionPolicy.quoted(name);
  switch(security){
   case "OPEN":c.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);break;
   case "OWE":if(!wifi.isEnhancedOpenSupported())throw new IllegalStateException("Enhanced Open is not supported by this unit.");c.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);break;
   case "WPA2":c.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);c.preSharedKey=NetworkCredentials.hex(password,64)?password:SettingsSessionPolicy.quoted(password);break;
   case "WPA3":if(!wifi.isWpa3SaeSupported())throw new IllegalStateException("WPA3 is not supported by this unit.");c.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);c.preSharedKey=SettingsSessionPolicy.quoted(password);break;
   case "WEP":c.setSecurityParams(WifiConfiguration.SECURITY_TYPE_WEP);c.wepTxKeyIndex=0;c.wepKeys[0]=NetworkCredentials.hex(password,10)||NetworkCredentials.hex(password,26)||NetworkCredentials.hex(password,58)?password:SettingsSessionPolicy.quoted(password);break;
   case "PEAP":case "TTLS":c.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);WifiEnterpriseConfig e=new WifiEnterpriseConfig();e.setEapMethod("PEAP".equals(security)?WifiEnterpriseConfig.Eap.PEAP:WifiEnterpriseConfig.Eap.TTLS);e.setPhase2Method(WifiEnterpriseConfig.Phase2.MSCHAPV2);e.setIdentity(input.getString("identity"));e.setAnonymousIdentity(input.getString("anonymous",""));e.setPassword(password);e.setDomainSuffixMatch(NetworkCredentials.domain(input.getString("domain")));String trust=null;for(String path:new String[]{"/apex/com.android.conscrypt/cacerts","/system/etc/security/cacerts"}){java.io.File directory=new java.io.File(path);String[] files=directory.list();if(files!=null&&files.length>0){trust=path;break;}}if(trust==null)throw new IllegalStateException("System CA certificate store unavailable. Certificate verification is required.");WifiEnterpriseConfig.class.getMethod("setCaPath",String.class).invoke(e,trust);c.enterpriseConfig=e;break;
   default:throw new IllegalArgumentException("Unsupported network security");
  }return c;
 }
 private SessionWifiConfig(){}
}
