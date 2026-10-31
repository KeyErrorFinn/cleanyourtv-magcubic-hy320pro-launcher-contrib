package com.gathin.tvhome;

import java.io.*;

/** Durable request history, including pre-request geometry. No promise of optical-focus undo. */
final class AutomaticProjectionJournal implements AutomaticProjectionControl.Journal {
    private final File directory,original,events;
    AutomaticProjectionJournal(File files){directory=new File(files,"projection-automatic");original=new File(directory,"original.bin");events=new File(directory,"events.bin");}
    static boolean pending(File files){File dir=new File(files,"projection-automatic");return new File(dir,"original.bin").exists()||new File(dir,"events.bin").exists();}
    public boolean exists(){return original.exists()||events.exists();}
    public void capture(AutomaticProjectionControl.Action action,AutomaticProjectionControl.Snapshot before)throws IOException{
        if(exists())throw new IOException("Original request record already exists");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create request history");
        if(!original.createNewFile())throw new IOException("Cannot reserve original request record");
        try(FileOutputStream file=new FileOutputStream(original);DataOutputStream out=new DataOutputStream(file)){
            out.writeUTF("CleanYourTV-automatic-request-v1");out.writeUTF(action.name());out.writeLong(System.currentTimeMillis());
            OrientationJournal.writeState(out,before.geometry);out.writeUTF(before.angle);out.writeUTF(before.request);out.writeUTF(before.queued);out.writeUTF(before.running);
            out.writeUTF("END_ORIGINAL");out.flush();file.getFD().sync();
        }
        record("ORIGINAL_CAPTURED_NO_MUTATION");
    }
    public void record(String event)throws IOException{
        try(FileOutputStream file=new FileOutputStream(events,true);DataOutputStream out=new DataOutputStream(file)){
            out.writeLong(System.currentTimeMillis());out.writeUTF(event);out.flush();file.getFD().sync();
        }
    }
    void finish()throws IOException{
        if(!exists())return;record("OWNER_CONFIRMED_PICTURE_FOCUS_NO_AUTOMATIC_UNDO");
        archive();
    }
    boolean maySettle(long now)throws IOException{
        try(DataInputStream in=new DataInputStream(new FileInputStream(original))){
            if(!"CleanYourTV-automatic-request-v1".equals(in.readUTF()))throw new IOException("Unknown adjustment record");
            AutomaticProjectionControl.Action action=AutomaticProjectionControl.Action.valueOf(in.readUTF());
            long started=in.readLong();long delay=action==AutomaticProjectionControl.Action.CALIBRATE?35000L:5000L;
            return started>0&&now>=started&&now-started>=delay;
        }
    }
    void settled()throws IOException{
        if(!exists())return;record("REQUEST_FLAGS_SETTLED_PHYSICAL_UNVERIFIED");
        archive();
    }
    private void archive()throws IOException{
        File history=new File(directory.getParentFile(),"projection-automatic-history");
        if(!history.isDirectory()&&!history.mkdirs())throw new IOException("Cannot retain request history");
        if(!directory.renameTo(new File(history,"request-"+java.util.UUID.randomUUID())))throw new IOException("Cannot archive request record");
    }
}
