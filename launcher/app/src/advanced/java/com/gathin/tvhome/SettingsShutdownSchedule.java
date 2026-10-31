package com.gathin.tvhome;
/** One owned deadline. Cancel/reschedule and deadline-claim are mutually exclusive. */
final class SettingsShutdownSchedule {
 private long generation,deadline;private boolean started;
 synchronized long plan(int seconds,long now,long sessionEnd){
  SettingsSessionPolicy.timer(seconds);if(started)throw new IllegalStateException("Shutdown has already started.");
  long next=seconds==0?0:now+seconds*1000L;if(next>=sessionEnd)throw new IllegalStateException("Restart the helper before scheduling this timer.");
  deadline=next;return ++generation;
 }
 synchronized boolean begin(long token,long now){if(started||token!=generation||deadline==0||now<deadline)return false;started=true;deadline=0;return true;}
 synchronized void cancel(){if(started)throw new IllegalStateException("Shutdown has already started.");generation++;deadline=0;}
 synchronized void abandon(){if(!started){generation++;deadline=0;}}
 synchronized void failed(){started=false;}
 synchronized long remaining(long now){return Math.max(0,(deadline-now+999)/1000);}
}
