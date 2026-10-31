package com.gathin.tvhome;
import org.junit.Test;import static org.junit.Assert.*;
public class SettingsSessionPolicyTest {
 @Test public void onlyTheExactLauncherUidIsAccepted(){SettingsSessionPolicy.caller(10123,10123);try{SettingsSessionPolicy.caller(10124,10123);fail();}catch(SecurityException expected){}}
 @Test public void systemAndShellCannotImpersonateTheClient(){for(int id:new int[]{0,1000,2000})try{SettingsSessionPolicy.caller(id,id);fail();}catch(SecurityException expected){}}
 @Test public void sessionExpiresAtTheExactBoundary(){SettingsSessionPolicy.live(100+SettingsSessionPolicy.LIFETIME-1,100,true);try{SettingsSessionPolicy.live(100+SettingsSessionPolicy.LIFETIME,100,true);fail();}catch(SecurityException expected){}}
 @Test public void debuggingOffOrInvalidMonotonicTimeRejectsOperations(){try{SettingsSessionPolicy.live(100,0,false);fail();}catch(SecurityException expected){}try{SettingsSessionPolicy.live(1,2,true);fail();}catch(SecurityException expected){}}
 @Test public void normalUserAppsAreEligible(){SettingsSessionPolicy.userPackage("local.hy320.compatibility",false);SettingsSessionPolicy.userPackage("com.netflix.mediaclient",false);}
 @Test public void coreAndProjectorPackagesStayProtectedEvenIfFlagsAreUnexpected(){for(String pkg:new String[]{"com.htc.magcubicos","com.hysd.vafocus","com.google.android.gms","com.android.vending","com.gathin.tvhome","com.softwinner.awsource"})try{SettingsSessionPolicy.userPackage(pkg,false);fail();}catch(SecurityException expected){}}
 @Test public void allPreinstalledPackagesAreProtected(){try{SettingsSessionPolicy.userPackage("example.app",true);fail();}catch(SecurityException expected){}}
 @Test public void permissionUndoKeyIsParsedWithoutRegexAmbiguity(){assertArrayEquals(new String[]{"example.app","android.permission.CAMERA"},SettingsSessionPolicy.permissionRecord("permission.example.app|android.permission.CAMERA"));}
 @Test public void malformedPermissionUndoKeysAreRejected(){for(String key:new String[]{"permission.app|", "permission.|CAMERA", "permission.app|CAMERA|OTHER", "other.app|CAMERA"})try{SettingsSessionPolicy.permissionRecord(key);fail();}catch(IllegalArgumentException expected){}}
 @Test public void ipv4IsNumericAndDoesNotResolveHostnames(){assertArrayEquals(new byte[]{(byte)192,(byte)168,0,1},SettingsSessionPolicy.ipv4("192.168.0.1"));for(String value:new String[]{"example.org","256.1.1.1","1.2.3","1.2.3.-1","1.2.3.4/24"})try{SettingsSessionPolicy.ipv4(value);fail();}catch(IllegalArgumentException expected){}}
 @Test public void clockBoundsAreExplicit(){SettingsSessionPolicy.timestamp(946684800000L);SettingsSessionPolicy.timestamp(2145916799999L);try{SettingsSessionPolicy.timestamp(2145916800000L);fail();}catch(IllegalArgumentException expected){}}
 @Test public void zoneMustBeReal(){SettingsSessionPolicy.zone("UTC");try{SettingsSessionPolicy.zone("Invented/Zone");fail();}catch(IllegalArgumentException expected){}}
 @Test public void onlySupportedTimersAreAccepted(){for(int t:new int[]{0,300,600,1200,3600})SettingsSessionPolicy.timer(t);try{SettingsSessionPolicy.timer(-1);fail();}catch(IllegalArgumentException expected){}}
 @Test public void networkLimitsUseUtf8Bytes(){SettingsSessionPolicy.network("Network","12345678",true);try{SettingsSessionPolicy.network("Network","short",true);fail();}catch(IllegalArgumentException expected){}try{SettingsSessionPolicy.network("12345678901234567890123456789012345","",false);fail();}catch(IllegalArgumentException expected){}}
 @Test public void credentialsAreQuotedWithoutShellInterpolation(){assertEquals("\"a\\\"b\\\\c\"",SettingsSessionPolicy.quoted("a\"b\\c"));}
 @org.junit.Test public void pairingUsesFingerprintRatherThanRawPublicKey(){org.junit.Assert.assertTrue(SettingsSessionPolicy.fingerprint("01:23:45:67:89:AB:CD:EF:01:23:45:67:89:AB:CD:EF"));org.junit.Assert.assertFalse(SettingsSessionPolicy.fingerprint("AAAA public-key owner@host"));org.junit.Assert.assertFalse(SettingsSessionPolicy.fingerprint(null));}
}
