package com.gathin.tvhome;
import org.junit.Test;import static org.junit.Assert.*;
public class SettingsShutdownScheduleTest {
 @Test public void neverStartsEarlyAndClaimsOnlyOnce(){SettingsShutdownSchedule s=new SettingsShutdownSchedule();long t=s.plan(300,1000,10000000);assertFalse(s.begin(t,300999));assertTrue(s.begin(t,301000));assertFalse(s.begin(t,301001));}
 @Test public void cancelledQueuedCallbackCannotShutdown(){SettingsShutdownSchedule s=new SettingsShutdownSchedule();long t=s.plan(300,0,10000000);s.cancel();assertFalse(s.begin(t,400000));assertEquals(0,s.remaining(1));}
 @Test public void rescheduleInvalidatesThePreviousCallback(){SettingsShutdownSchedule s=new SettingsShutdownSchedule();long a=s.plan(300,0,10000000),b=s.plan(600,1,10000000);assertFalse(s.begin(a,400000));assertFalse(s.begin(b,600000));assertTrue(s.begin(b,600001));}
 @Test public void cancelCannotFalselySucceedAfterShutdownIsClaimed(){SettingsShutdownSchedule s=new SettingsShutdownSchedule();long t=s.plan(300,0,10000000);assertTrue(s.begin(t,300000));try{s.cancel();fail();}catch(IllegalStateException expected){}}
 @Test public void offAndOwnerExitInvalidateTheDeadline(){SettingsShutdownSchedule s=new SettingsShutdownSchedule();long t=s.plan(300,0,10000000);s.plan(0,1,10000000);assertFalse(s.begin(t,400000));t=s.plan(300,0,10000000);s.abandon();assertFalse(s.begin(t,400000));}
 @Test public void timerMustFitInsideTheAuthorisedSession(){SettingsShutdownSchedule s=new SettingsShutdownSchedule();try{s.plan(3600,0,3600000);fail();}catch(IllegalStateException expected){}assertEquals(0,s.remaining(0));}
 @Test public void aRefusedShutdownCanBeScheduledAgain(){SettingsShutdownSchedule s=new SettingsShutdownSchedule();long t=s.plan(300,0,10000000);assertTrue(s.begin(t,300000));s.failed();t=s.plan(300,400000,10000000);assertTrue(s.begin(t,700000));}
}
