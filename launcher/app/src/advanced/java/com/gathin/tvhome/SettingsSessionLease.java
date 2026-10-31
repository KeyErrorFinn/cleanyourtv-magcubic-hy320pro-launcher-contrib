package com.gathin.tvhome;
/** Optional session expiry; debugging off, owner death and reboot always revoke the helper. */
final class SettingsSessionLease {
 private final long started;private long deadline;
 SettingsSessionLease(long now){started=now;deadline=now+SettingsSessionPolicy.LIFETIME;}
 synchronized void select(boolean untilClosed,long now){if(now<started)throw new IllegalStateException("Invalid session clock");deadline=untilClosed?Long.MAX_VALUE:now+SettingsSessionPolicy.LIFETIME;}
 synchronized void check(long now,boolean debugging){if(!debugging||now<started||now>=deadline)throw new SecurityException("ADB helper is not connected. Start it from your computer.");}
 synchronized long deadline(){return deadline;}
 synchronized boolean untilClosed(){return deadline==Long.MAX_VALUE;}
 synchronized long remaining(long now){return untilClosed()?-1:Math.max(0,deadline-now);}
}
