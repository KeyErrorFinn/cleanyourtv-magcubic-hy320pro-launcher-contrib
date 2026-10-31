package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;

public class KeyboardSelectionChangeTest {
    static class Fake implements KeyboardSelectionChange.Backend {
        String id="old", subtype=null, savedId, savedSubtype;
        int writes; boolean failTarget, failCapture;
        public String id(){return id;}
        public String subtype(){return subtype;}
        public boolean id(String value){writes++;if(failTarget && "new".equals(value))return false;id=value;return true;}
        public boolean subtype(String value){writes++;subtype=value;return true;}
        public void capture(String a,String b){if(failCapture)throw new IllegalStateException();savedId=a;savedSubtype=b;}
    }
    @Test public void changesBothAndRecordsAbsentSubtype(){Fake b=new Fake();KeyboardSelectionChange.apply(b,"old",null,"new");assertEquals("new",b.id);assertEquals("-1",b.subtype);assertEquals("old",b.savedId);assertNull(b.savedSubtype);}
    @Test public void failedSelectionRestoresAbsentSubtype(){Fake b=new Fake();b.failTarget=true;try{KeyboardSelectionChange.apply(b,"old",null,"new");fail();}catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("restored"));}assertEquals("old",b.id);assertNull(b.subtype);}
    @Test public void staleStateDoesNotWrite(){Fake b=new Fake();try{KeyboardSelectionChange.apply(b,"stale",null,"new");fail();}catch(IllegalStateException expected){}assertEquals(0,b.writes);}
    @Test public void failedJournalDoesNotWrite(){Fake b=new Fake();b.failCapture=true;try{KeyboardSelectionChange.apply(b,"old",null,"new");fail();}catch(IllegalStateException expected){}assertEquals(0,b.writes);}
    @Test public void unchangedSelectionDoesNotResetSubtype(){Fake b=new Fake();b.subtype="123";KeyboardSelectionChange.apply(b,"old","123","old");assertEquals("123",b.subtype);assertEquals(0,b.writes);}
}
