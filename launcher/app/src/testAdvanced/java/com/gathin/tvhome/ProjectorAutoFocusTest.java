package com.gathin.tvhome;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class ProjectorAutoFocusTest {
    private static class Store implements ProjectorAutoFocus.Store {
        String value="1";int writes,reads;boolean drift,drop,fail;
        @Override public String read(){if(drift && ++reads==2)return "0";return value;}
        @Override public void write(String v){writes++;if(fail)throw new IllegalStateException("Denied");if(!drop)value=v;}
    }
    private static class Journal implements OrientationPilot.Journal {
        boolean exists;
        @Override public boolean exists(){return exists;}
        @Override public OrientationPilot.State original(){throw new UnsupportedOperationException();}
        @Override public void create(OrientationPilot.State state){throw new UnsupportedOperationException();}
        @Override public void record(String stage,OrientationPilot.State state){throw new UnsupportedOperationException();}
    }
    private interface Checked{void run()throws Exception;}
    private static void fails(Checked run)throws Exception{try{run.run();fail("Expected stop");}catch(Exception expected){}}
    @Test public void durableUndoPrecedesTypedPreferenceWrite()throws Exception{
        Store s=new Store();List<String> originals=new ArrayList<>();
        ProjectorAutoFocus.set(s,v->{assertEquals(0,s.writes);originals.add(v);},new Journal(),new Journal(),false);
        assertEquals("0",s.value);assertEquals(1,s.writes);assertEquals("1",originals.get(0));assertFalse(OrientationPilot.busy());
    }
    @Test public void unchangedPreferenceDoesNotWriteOrCapture()throws Exception{
        Store s=new Store();ProjectorAutoFocus.set(s,v->{throw new IllegalStateException("Should not capture");},new Journal(),new Journal(),true);assertEquals(0,s.writes);
    }
    @Test public void failedBackupOrConcurrentStatePreventsWrite()throws Exception{
        Store s=new Store();fails(()->ProjectorAutoFocus.set(s,v->{throw new IllegalStateException("Disk full");},new Journal(),new Journal(),false));assertEquals(0,s.writes);
        s.drift=true;fails(()->ProjectorAutoFocus.set(s,v->{},new Journal(),new Journal(),false));assertEquals(0,s.writes);
    }
    @Test public void eitherRecoveryScopeBlocksPreferenceChange()throws Exception{
        for(int i=0;i<2;i++){Store s=new Store();Journal a=new Journal(),b=new Journal();if(i==0)a.exists=true;else b.exists=true;
            fails(()->ProjectorAutoFocus.set(s,v->{},a,b,false));assertEquals(0,s.writes);}
    }
    @Test public void missingUnexpectedOrUnconfirmedValuesFailClosed()throws Exception{
        for(String bad:new String[]{"","true","2"}){Store s=new Store();s.value=bad;fails(()->ProjectorAutoFocus.set(s,v->{},new Journal(),new Journal(),false));assertEquals(0,s.writes);}
        Store s=new Store();s.drop=true;fails(()->ProjectorAutoFocus.set(s,v->{},new Journal(),new Journal(),false));assertEquals(1,s.writes);
        Store denied=new Store();denied.fail=true;List<String> undo=new ArrayList<>();fails(()->ProjectorAutoFocus.set(denied,undo::add,new Journal(),new Journal(),false));assertEquals("1",undo.get(0));
    }
    @Test public void opticalWorkerRetainsLockWhenFocusRequestIsRefused()throws Exception{
        assertTrue(OrientationPilot.acquireOpticalOperation());try{Store s=new Store();fails(()->ProjectorAutoFocus.set(s,v->{},new Journal(),new Journal(),false));assertEquals(0,s.writes);assertTrue(OrientationPilot.busy());}finally{OrientationPilot.releaseOpticalOperation();}
    }
}
