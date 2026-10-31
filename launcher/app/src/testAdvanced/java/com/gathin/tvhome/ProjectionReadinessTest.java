package com.gathin.tvhome;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
public class ProjectionReadinessTest {
 @Test public void cooldownReleasesSameOpenControl()throws Exception{
  long[] time={0};int[] reads={0};ProjectionReadiness.await(()->{reads[0]++;if(time[0]<35000)throw new ProjectionReadiness.Pending("settling");},new AtomicBoolean(),()->time[0],ms->time[0]+=ms);
  assertEquals(35000,time[0]);assertTrue(reads[0]>1);
 }
 @Test public void activityMustBecomeIdleEvenAfterCooldown()throws Exception{
  long[] time={0};ProjectionReadiness.await(()->{if(time[0]<42000)throw new ProjectionReadiness.Pending("motor active");},new AtomicBoolean(),()->time[0],ms->time[0]+=ms);assertEquals(42000,time[0]);
 }
 @Test public void realErrorIsNotRetried()throws Exception{
  int[] reads={0};try{ProjectionReadiness.await(()->{reads[0]++;throw new java.io.IOException("bad receipt");},new AtomicBoolean(),()->0L,ms->fail());fail();}catch(java.io.IOException expected){assertEquals("bad receipt",expected.getMessage());}assertEquals(1,reads[0]);
 }
 @Test public void persistentActivityTimesOut()throws Exception{
  long[] time={0};try{ProjectionReadiness.await(()->{throw new ProjectionReadiness.Pending("motor active");},new AtomicBoolean(),()->time[0],ms->time[0]+=ms);fail();}catch(IllegalStateException expected){assertTrue(expected.getCause() instanceof ProjectionReadiness.Pending);}assertEquals(45000,time[0]);
 }
 @Test public void pageClosureStopsReads()throws Exception{
  AtomicBoolean closed=new AtomicBoolean();int[] reads={0};try{ProjectionReadiness.await(()->{reads[0]++;throw new ProjectionReadiness.Pending("settling");},closed,()->0L,ms->closed.set(true));fail();}catch(InterruptedException expected){}assertEquals(1,reads[0]);
 }
 @Test public void idleDoesNotWait()throws Exception{
  int[] reads={0};ProjectionReadiness.await(()->reads[0]++,new AtomicBoolean(),()->0L,ms->fail());assertEquals(1,reads[0]);
 }
}
