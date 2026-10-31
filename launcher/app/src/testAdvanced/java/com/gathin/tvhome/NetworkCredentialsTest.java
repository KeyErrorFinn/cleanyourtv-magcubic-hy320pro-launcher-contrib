package com.gathin.tvhome;
import org.junit.Test;import static org.junit.Assert.*;
public class NetworkCredentialsTest {
 @Test public void securityCannotBeSilentlyDowngraded(){try{NetworkCredentials.validate("Network","password","INVENTED","","");fail();}catch(IllegalArgumentException expected){}}
 @Test public void wpa2AcceptsPassphraseOrExactHexKey(){NetworkCredentials.validate("Network","12345678","WPA2","","");NetworkCredentials.validate("Network",new String(new char[64]).replace('\0','a'),"WPA2","","");for(String pass:new String[]{"short",new String(new char[64]).replace('\0','z')})try{NetworkCredentials.validate("Network",pass,"WPA2","","");fail();}catch(IllegalArgumentException expected){}}
 @Test public void saeHasItsOwnPasswordRules(){NetworkCredentials.validate("Network","x","WPA3","","");try{NetworkCredentials.validate("Network","","WPA3","","");fail();}catch(IllegalArgumentException expected){}}
 @Test public void wepRequiresAnActualKeyShape(){for(String key:new String[]{"abcde","0123456789","00112233445566778899aabbcc"})NetworkCredentials.validate("Network",key,"WEP","","");for(String key:new String[]{"1234","nothex1234","123456"})try{NetworkCredentials.validate("Network",key,"WEP","","");fail();}catch(IllegalArgumentException expected){}}
 @Test public void enterpriseCannotOmitServerValidation(){for(String domain:new String[]{"","https://example.org","example.org/other"," example.org","example..org"})try{NetworkCredentials.validate("Network","password","PEAP","user",domain);fail(domain);}catch(IllegalArgumentException expected){}}
 @Test public void enterpriseUsesValidatedDomainAndIdentity(){assertEquals("example.org",NetworkCredentials.domain("example.org"));NetworkCredentials.validate("Network","password","TTLS","user","example.org");try{NetworkCredentials.validate("Network","password","PEAP","","example.org");fail();}catch(IllegalArgumentException expected){}}
 @Test public void enhancedOpenNeedsNoPassword(){NetworkCredentials.validate("Network","","OWE","","");}
 @Test public void aliasSupportsExactAbsenceAndRejectsControlCharacters(){SettingsSessionPolicy.alias(null);SettingsSessionPolicy.alias("Living room speaker");for(String alias:new String[]{"","   ","Remote\nOther",new String(new char[249]).replace('\0','a')})try{SettingsSessionPolicy.alias(alias);fail();}catch(IllegalArgumentException expected){}}
}
