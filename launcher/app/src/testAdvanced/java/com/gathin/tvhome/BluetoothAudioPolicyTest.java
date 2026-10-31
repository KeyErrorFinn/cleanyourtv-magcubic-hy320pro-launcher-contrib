package com.gathin.tvhome;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class BluetoothAudioPolicyTest {
    private static Set<UUID> services(UUID... ids){return new HashSet<>(Arrays.asList(ids));}
    @Test public void pairedClassicAndDualMediaSinksCanExposeAudioControls(){
        assertTrue(BluetoothAudioPolicy.audio(true,1,0x400,services(BluetoothAudioPolicy.SINK)));
        assertTrue(BluetoothAudioPolicy.audio(true,3,0x400,services(BluetoothAudioPolicy.SINK)));
    }
    @Test public void unknownOrUnpairedOrLeOnlyDeviceStaysProtected(){
        assertFalse(BluetoothAudioPolicy.audio(false,1,0x400,services(BluetoothAudioPolicy.SINK)));
        assertFalse(BluetoothAudioPolicy.audio(true,0,0x400,services(BluetoothAudioPolicy.SINK)));
        assertFalse(BluetoothAudioPolicy.audio(true,2,0x400,services(BluetoothAudioPolicy.SINK)));
        assertFalse(BluetoothAudioPolicy.audio(true,1,-1,services(BluetoothAudioPolicy.SINK)));
    }
    @Test public void remotePeripheralClassCannotBecomeAudioEvenWithSinkUuid(){
        assertFalse(BluetoothAudioPolicy.audio(true,1,0x500,services(BluetoothAudioPolicy.SINK)));
        assertFalse(BluetoothAudioPolicy.audio(true,3,0x500,services(BluetoothAudioPolicy.SINK)));
    }
    @Test public void hidRolesOverrideAnAudioClassAndSinkUuid(){
        assertFalse(BluetoothAudioPolicy.audio(true,1,0x400,services(BluetoothAudioPolicy.SINK,BluetoothAudioPolicy.HID)));
        assertFalse(BluetoothAudioPolicy.audio(true,3,0x400,services(BluetoothAudioPolicy.SINK,BluetoothAudioPolicy.HOGP)));
    }
    @Test public void missingCachedServicesDoNotTriggerDiscoveryOrAudioAssumptions(){
        assertFalse(BluetoothAudioPolicy.audio(true,1,0x400,null));
        assertFalse(BluetoothAudioPolicy.audio(true,1,0x400,services()));
        assertFalse(BluetoothAudioPolicy.audio(true,1,0x400,services(UUID.fromString("0000110e-0000-1000-8000-00805f9b34fb"))));
    }
    @Test public void connectedOrUnknownAclCannotBeForgottenEvenWithDisconnectedAudio(){
        assertFalse(BluetoothAudioPolicy.forget(true,true,true,0));
        assertFalse(BluetoothAudioPolicy.forget(true,false,false,0));
        assertFalse(BluetoothAudioPolicy.forget(false,true,false,0));
    }
    @Test public void onlyVerifiedFullyDisconnectedAudioCanBeForgotten(){
        assertTrue(BluetoothAudioPolicy.forget(true,true,false,0));
        for(int state:new int[]{-1,1,2,3})assertFalse(BluetoothAudioPolicy.forget(true,true,false,state));
    }
}
