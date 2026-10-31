package com.gathin.tvhome;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** App-private immutable original plus append-only, fsynced operation records. Never overwritten on retry. */
final class OrientationJournal implements OrientationPilot.Journal {
    private static final String FORMAT = "CleanYourTV-orientation-pilot-v2";
    enum Scope { ORIENTATION, MANUAL_CORNERS, RESET, SCALING }
    private final Scope scope;
    private final File directory, original, events, history;
    private final File[] otherDirectories;
    OrientationJournal(File filesDirectory) { this(filesDirectory, Scope.ORIENTATION); }
    OrientationJournal(File filesDirectory, Scope scope) {
        this.scope=scope;
        String name=scope==Scope.ORIENTATION?"orientation-pilot":scope==Scope.MANUAL_CORNERS?"manual-corners":scope==Scope.RESET?"projection-reset":"projection-scaling";
        directory = new File(filesDirectory,name);
        history = new File(filesDirectory,scope==Scope.ORIENTATION?"orientation-history":scope==Scope.MANUAL_CORNERS?"manual-corner-history":scope==Scope.RESET?"projection-reset-history":"projection-scaling-history");
        java.util.List<File> others=new java.util.ArrayList<>();
        for(String candidate:new String[]{"orientation-pilot","manual-corners","projection-reset","projection-scaling"})
            if(!candidate.equals(name))others.add(new File(filesDirectory,candidate));
        otherDirectories=others.toArray(new File[0]);
        original = new File(directory, "original.bin"); events = new File(directory, "events.bin");
    }
    @Override public boolean exists() { return original.exists() || events.exists(); }
    private void validate(OrientationPilot.State state){if(scope==Scope.MANUAL_CORNERS||scope==Scope.RESET)state.validateManual(true);else if(scope==Scope.SCALING)state.validateScaled(true,true);else state.validate(true);}
    @Override public boolean conflicts() {
        if(AutomaticProjectionJournal.pending(directory.getParentFile()))return true;
        for(File other:otherDirectories)if(new File(other,"original.bin").exists()||new File(other,"events.bin").exists())return true;
        return false;
    }
    @Override public OrientationPilot.State original() throws IOException {
        return readOriginal(original);
    }
    private static OrientationPilot.State readOriginal(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            if (!FORMAT.equals(in.readUTF())) throw new IOException("Unknown journal format");
            OrientationPilot.State state = readState(in);
            if (!"END_ORIGINAL".equals(in.readUTF()) || in.read() != -1) throw new IOException("Incomplete original backup");
            return state;
        }
    }
    /** Retained setup geometry, never a factory default. Existing originals are not discarded. */
    OrientationPilot.State initialGeometry() throws IOException {
        File setup=new File(directory.getParentFile(),directory.getName()+"-setup.bin");
        if(setup.exists())return readOriginal(setup);
        java.util.List<File> candidates=new java.util.ArrayList<>();
        if(exists())candidates.add(directory);
        if(history.exists()) {
            File[] archives=history.listFiles(File::isDirectory);
            if(archives==null || archives.length>256)throw new IOException("Geometry history unavailable");
            java.util.Collections.addAll(candidates,archives);
        }
        OrientationPilot.State first=null;long earliest=Long.MAX_VALUE;
        for(File candidate:candidates) {
            OrientationPilot.State state=readOriginal(new File(candidate,"original.bin"));validate(state);
            long time;
            try(DataInputStream in=new DataInputStream(new FileInputStream(new File(candidate,"events.bin")))) {
                if(!FORMAT.equals(in.readUTF()))throw new IOException("Unrecognised geometry history");
                time=in.readLong();
                if(time<=0 || !"ORIGINAL_CAPTURED_NO_MUTATION".equals(in.readUTF()) || !in.readBoolean()
                        || !state.same(readState(in)) || !"END_EVENT".equals(in.readUTF()))throw new IOException("Incomplete geometry history");
            }
            if(time<earliest){earliest=time;first=state;}
            else if(time==earliest && first!=null && !first.same(state))throw new IOException("Ambiguous setup history");
        }
        if(first==null)throw new IOException("No saved setup geometry yet");
        if(!setup.createNewFile())return readOriginal(setup);
        try(FileOutputStream file=new FileOutputStream(setup);DataOutputStream out=new DataOutputStream(file)) {
            out.writeUTF(FORMAT);writeState(out,first);out.writeUTF("END_ORIGINAL");out.flush();file.getFD().sync();
        }
        if(!first.same(readOriginal(setup)))throw new IOException("Setup geometry backup could not be verified");
        return first;
    }
    @Override public void create(OrientationPilot.State state) throws IOException {
        if (exists()) throw new IOException("Original journal already exists");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create private journal directory");
        if (!original.createNewFile()) throw new IOException("Refusing to replace original backup");
        try (FileOutputStream file = new FileOutputStream(original);
             DataOutputStream out = new DataOutputStream(file)) {
            out.writeUTF(FORMAT); writeState(out, state); out.writeUTF("END_ORIGINAL"); out.flush(); file.getFD().sync();
        }
        // Read back original bytes before any optical write. Corruption blocks operations.
        if (!state.same(original())) throw new IOException("Original backup verification failed");
        record("ORIGINAL_CAPTURED_NO_MUTATION", state);
    }
    @Override public void record(String stage, OrientationPilot.State expected) throws IOException {
        if (!original.isFile()) throw new IOException("Original backup missing");
        try (FileOutputStream file = new FileOutputStream(events, true);
             DataOutputStream out = new DataOutputStream(file)) {
            out.writeUTF(FORMAT); out.writeLong(System.currentTimeMillis()); out.writeUTF(stage);
            out.writeBoolean(expected != null); if (expected != null) writeState(out, expected);
            out.writeUTF("END_EVENT"); out.flush(); file.getFD().sync();
        }
    }
    @Override public void verifyApplied(OrientationPilot.State confirmed) throws IOException {
        // Parse the entire event stream: incomplete tails or failed operations cannot be dismissed.
        validate(original());
        String last = null;
        OrientationPilot.State expected = null;
        try (DataInputStream in = new DataInputStream(new FileInputStream(events))) {
            while (in.available() > 0) {
                if (!FORMAT.equals(in.readUTF())) throw new IOException("Unknown event format");
                in.readLong(); last = in.readUTF();
                expected = in.readBoolean() ? readState(in) : null;
                if (!"END_EVENT".equals(in.readUTF())) throw new IOException("Incomplete event");
            }
        }
        if (!("APPLY_STORED_READBACK_OK_PHYSICAL_UNVERIFIED".equals(last)
                || "RESTORE_STORED_READBACK_OK_PHYSICAL_UNVERIFIED".equals(last)
                || "OWNER_CONFIRMED_PICTURE".equals(last) || "SETTINGS_SAVED_READBACK_ONLY".equals(last))
                || expected == null || !expected.same(confirmed))
            throw new IOException("Unfinished operation or changed state; original recovery retained");
    }
    @Override public void complete(OrientationPilot.State confirmed) throws IOException {
        verifyApplied(confirmed);
        record("OWNER_CONFIRMED_PICTURE", confirmed);
        archive();
    }
    /** Normal Apply saves verified settings without claiming an attended optical check. */
    void saveVerified(OrientationPilot.State confirmed) throws IOException {
        verifyApplied(confirmed);
        record("SETTINGS_SAVED_READBACK_ONLY",confirmed);
        archive();
    }
    private void archive() throws IOException {
        File archive = history;
        if (!archive.isDirectory() && !archive.mkdirs()) throw new IOException("Cannot create recovery archive");
        File destination = new File(archive, "operation-" + java.util.UUID.randomUUID());
        // One same-filesystem directory rename preserves original/events together. Failure blocks new Apply.
        if (destination.exists() || !directory.renameTo(destination)) throw new IOException("Cannot archive recovery journal");
        if (exists() || !destination.isDirectory()) throw new IOException("Archive result uncertain");
    }
    static void writeState(DataOutputStream out, OrientationPilot.State state) throws IOException {
        for (OrientationPilot.Property p : OrientationPilot.Property.values()) out.writeUTF(state.value(p));
        for (OrientationPilot.Guard g : OrientationPilot.Guard.values()) out.writeUTF(state.guard(g));
        out.writeInt(state.mirror);
        out.writeBoolean(state.panel != null);
        if (state.panel != null) {
            out.writeUTF(state.panel.path.name()); out.writeInt(state.panel.stored); out.writeUTF(state.panel.surroundings);
        }
    }
    private static OrientationPilot.State readState(DataInputStream in) throws IOException {
        String[] values = new String[OrientationPilot.Property.values().length];
        String[] guards = new String[OrientationPilot.Guard.values().length];
        for (int i = 0; i < values.length; i++) values[i] = in.readUTF();
        for (int i = 0; i < guards.length; i++) guards[i] = in.readUTF();
        int mirror = in.readInt();
        MirrorField.Value panel = null;
        if (in.readBoolean()) {
            try { panel = new MirrorField.Value(MirrorField.Path.valueOf(in.readUTF()), in.readInt(), in.readUTF()); }
            catch (IllegalArgumentException invalid) { throw new IOException("Invalid original panel field", invalid); }
        }
        return new OrientationPilot.State(values, guards, mirror, panel);
    }
}
