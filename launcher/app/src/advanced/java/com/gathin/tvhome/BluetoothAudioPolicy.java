package com.gathin.tvhome;

import java.util.Set;
import java.util.UUID;

/** Conservative media-audio classification; unknown devices and HID roles stay protected. */
final class BluetoothAudioPolicy {
    static final UUID SINK=UUID.fromString("0000110b-0000-1000-8000-00805f9b34fb");
    static final UUID HID=UUID.fromString("00001124-0000-1000-8000-00805f9b34fb");
    static final UUID HOGP=UUID.fromString("00001812-0000-1000-8000-00805f9b34fb");
    static boolean audio(boolean bonded,int type,int major,Set<UUID> services){
        return bonded&&(type==1||type==3)&&major==0x400&&services!=null&&services.contains(SINK)&&!services.contains(HID)&&!services.contains(HOGP);
    }
    static boolean forget(boolean audio,boolean aclKnown,boolean aclConnected,int profileState){return audio&&aclKnown&&!aclConnected&&profileState==0;}
    private BluetoothAudioPolicy(){}
}
