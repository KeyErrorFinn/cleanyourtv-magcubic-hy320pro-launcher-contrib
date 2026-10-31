package com.gathin.tvhome;

import org.junit.Test;
import java.util.EnumMap;
import static org.junit.Assert.*;

public class AutomaticTimeControlTest {
    private static final class Fixture implements AutomaticTimeControl.Backend, AutomaticTimeControl.Journal {
        final EnumMap<AutomaticTimeControl.Key,String> values = new EnumMap<>(AutomaticTimeControl.Key.class);
        final EnumMap<AutomaticTimeControl.Key,String> originals = new EnumMap<>(AutomaticTimeControl.Key.class);
        boolean captureFails, writeRefused, wrongReadback;
        int writes;
        final AutomaticTimeControl control = new AutomaticTimeControl(this, this);
        @Override public String read(AutomaticTimeControl.Key key) { return values.get(key); }
        @Override public boolean write(AutomaticTimeControl.Key key,String value) {
            writes++;
            assertTrue("original must be saved before a write", originals.containsKey(key));
            if (writeRefused) return false;
            if (!wrongReadback) values.put(key, value);
            return true;
        }
        @Override public boolean recorded(AutomaticTimeControl.Key key) { return originals.containsKey(key); }
        @Override public String original(AutomaticTimeControl.Key key) { return originals.get(key); }
        @Override public void capture(AutomaticTimeControl.Key key,String value) {
            if (captureFails) throw new IllegalStateException("disk full");
            if (!recorded(key)) originals.put(key, value);
        }
    }
    private static final AutomaticTimeControl.Key TIME=AutomaticTimeControl.Key.TIME;
    private static final AutomaticTimeControl.Key ZONE=AutomaticTimeControl.Key.ZONE;
    @Test public void restoresAbsentRatherThanCreatingDefault() {
        Fixture f=new Fixture(); f.control.set(TIME,null,true); f.control.restore(TIME,"1");
        assertNull(f.read(TIME)); assertTrue(f.recorded(TIME));
    }
    @Test public void repeatedChangesPreserveFirstOriginal() {
        Fixture f=new Fixture(); f.values.put(TIME,"1");
        f.control.set(TIME,"1",false); f.control.set(TIME,"0",true);
        assertEquals("1",f.original(TIME));
    }
    @Test public void staleDialogCannotOverwriteAnotherWriter() {
        Fixture f=new Fixture(); f.values.put(TIME,"1");
        assertThrows(IllegalStateException.class,()->f.control.set(TIME,"0",false)); assertEquals(0,f.writes);
    }
    @Test public void failedJournalPreventsMutation() {
        Fixture f=new Fixture(); f.captureFails=true;
        assertThrows(IllegalStateException.class,()->f.control.set(TIME,null,true)); assertEquals(0,f.writes);
    }
    @Test public void providerRefusalKeepsRecoveryRecord() {
        Fixture f=new Fixture(); f.writeRefused=true;
        assertThrows(IllegalStateException.class,()->f.control.set(TIME,null,true)); assertTrue(f.recorded(TIME));
    }
    @Test public void successWithoutReadbackIsFailure() {
        Fixture f=new Fixture(); f.wrongReadback=true;
        assertThrows(IllegalStateException.class,()->f.control.set(TIME,null,true));
    }
    @Test public void restoreWithoutOriginalCannotWrite() {
        Fixture f=new Fixture(); assertThrows(IllegalStateException.class,()->f.control.restore(TIME,null)); assertEquals(0,f.writes);
    }
    @Test public void zoneChangeDoesNotTouchAutomaticClock() {
        Fixture f=new Fixture(); f.values.put(TIME,"1"); f.values.put(ZONE,"0"); f.control.set(ZONE,"0",true);
        assertEquals("1",f.read(TIME)); assertFalse(f.recorded(TIME)); assertEquals("0",f.original(ZONE));
    }
}
