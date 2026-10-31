package com.gathin.tvhome;
import org.junit.Test;import static org.junit.Assert.*;
public class SettingsSessionLeaseTest {
 @Test public void defaultExpiresAfterTwoHours(){SettingsSessionLease l=new SettingsSessionLease(100);l.check(100+SettingsSessionPolicy.LIFETIME-1,true);try{l.check(100+SettingsSessionPolicy.LIFETIME,true);fail();}catch(SecurityException expected){}}
 @Test public void ownerCanKeepSessionUntilDebuggingCloses(){SettingsSessionLease l=new SettingsSessionLease(100);l.select(true,200);assertTrue(l.untilClosed());assertEquals(-1,l.remaining(300));l.check(100+SettingsSessionPolicy.LIFETIME*3,true);try{l.check(300,false);fail();}catch(SecurityException expected){}}
 @Test public void returningToTimedModeStartsNewExplicitWindow(){SettingsSessionLease l=new SettingsSessionLease(100);l.select(true,200);l.select(false,500);assertEquals(500+SettingsSessionPolicy.LIFETIME,l.deadline());assertFalse(l.untilClosed());}
 @Test public void clockRollbackNeverExtendsAuthority(){SettingsSessionLease l=new SettingsSessionLease(100);try{l.check(99,true);fail();}catch(SecurityException expected){}try{l.select(true,99);fail();}catch(IllegalStateException expected){}}
}
