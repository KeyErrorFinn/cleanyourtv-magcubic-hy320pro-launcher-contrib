package com.gathin.tvhome;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class OrientationPilotTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static MirrorField.Value field(int mode) {
        return new MirrorField.Value(MirrorField.Path.OEM, mode, "0".repeat(64));
    }
    private static String[] points() { return new String[]{"229","560","771","0","539","0","404","400"}; }
    private static OrientationPilot.State initial() {
        String[] values = {"0", "100.0", "1e2", "+0229", "560", "771", "0", "539", "0", "404", "400",
                "-11", "23", "-37", "41", "59", "-67", "79", "-83"};
        String[] guards = {"Projector_20260713.153921", "34", "optics", "1920x1080", "0", "0", "1", "true", "1", "1", "0", "0", "0,0,0,0,0,0,0,0", "0"};
        return new OrientationPilot.State(values, guards, 0, field(0));
    }
    private static class MemoryJournal implements OrientationPilot.Journal {
        OrientationPilot.State original;
        final List<String> records = new ArrayList<>();
        boolean failCreate, failRecord;
        @Override public boolean exists() { return original != null; }
        @Override public OrientationPilot.State original() { return original; }
        @Override public void create(OrientationPilot.State state) throws Exception {
            if (failCreate || original != null) throw new Exception("journal create refused"); original = state;
        }
        @Override public void record(String stage, OrientationPilot.State state) throws Exception {
            if (failRecord) throw new Exception("journal fsync failed"); records.add(stage);
        }
    }
    private static class Fake implements OrientationPilot.Backend {
        OrientationPilot.State state = initial();
        final MemoryJournal journal;
        final List<String> mutations = new ArrayList<>();
        int attempts, failAt, returnAt, readCount, driftAt;
        boolean gate, dropProperty, journalFailureAfterWrite;
        AtomicBoolean cancel;
        int cancelAt;
        CountDownLatch entered, release;
        boolean flippedH, flippedV;
        int[][] requestedPoints;
        Fake(MemoryJournal journal) { this.journal = journal; }
        @Override public void resolve() { }
        @Override public void prepareWrite() throws Exception {
            if (gate) throw new IllegalStateException("unreviewed native mirror result");
        }
        @Override public OrientationPilot.State read() {
            readCount++;
            if (readCount == driftAt) state = state.property(OrientationPilot.Property.D_LTX,"230");
            return state;
        }
        private int attempt(String name) throws Exception {
            assertNotNull("original must exist before mutation", journal.original);
            assertEquals("BEFORE_" + name, journal.records.get(journal.records.size()-1));
            attempts++; mutations.add(name);
            if (entered != null) { entered.countDown(); if (!release.await(5, TimeUnit.SECONDS)) throw new Exception("test latch timeout"); }
            if (attempts == failAt) throw new Exception("injected uncertain delivery");
            if (attempts == cancelAt) cancel.set(true);
            return attempts == returnAt ? -1 : 0;
        }
        @Override public int flip(boolean h, boolean v) throws Exception {
            int result = attempt("FLIP"); flippedH = h; flippedV = v; return result;
        }
        @Override public MirrorReceipt mirror(int mode) throws Exception {
            MirrorField.Value before = state.panel;
            int result = attempt("MIRROR"); state = state.mirror(OrientationMath.nativeBits(mode));
            return new MirrorReceipt(result == 0 ? -1 : 0, OrientationMath.nativeCommand(mode,true,true),
                    before, state.panel, state.mirror, "Projector_20260713.153921");
        }
        @Override public int coordinates(int[][] points) throws Exception {
            int result = attempt("COORDINATES"); requestedPoints = points; return result;
        }
        @Override public void persist(OrientationPilot.Property property, String value) throws Exception {
            attempt(property.name()); if (!dropProperty) state = state.property(property, value);
            if (journalFailureAfterWrite) journal.failRecord = true;
        }
    }
    private interface Checked { void run() throws Exception; }
    private static void fails(Checked action) throws Exception {
        try { action.run(); fail("Expected stop"); } catch (Exception expected) { assertNotNull(expected); }
    }
    @Test public void identityInverseCompositionAndExactEncodings() {
        String[] p = {"+001", "-2", "03", "+4", "-5", "06", "7", "-08"};
        for (int a=0;a<4;a++) for(int b=0;b<4;b++) {
            assertArrayEquals(p, OrientationMath.permute(OrientationMath.permute(p,a,b),b,a));
            assertArrayEquals(p, OrientationMath.permute(p,a,a));
            for(int c=0;c<4;c++) assertArrayEquals(OrientationMath.permute(p,a,c),
                    OrientationMath.permute(OrientationMath.permute(p,a,b),b,c));
        }
    }
    @Test public void nativeFlagsAndAvailability() {
        int[] nativeValues={8,9,11,10};
        for(int mode=0;mode<4;mode++) {
            assertEquals(nativeValues[mode],OrientationMath.nativeCommand(mode,true,true));
            assertEquals(mode,OrientationMath.nativeCommand(mode,false,true));
            assertEquals(mode,OrientationMath.nativeCommand(mode,true,false));
            assertEquals(mode,OrientationMath.nativeCommand(mode,false,false));
        }
    }
    @Test public void signedRasterConversionAndBoundaries() throws Exception {
        int[][] p=OrientationMath.hal(points(),1920,1080);
        assertArrayEquals(new int[]{229,560},p[0]); assertArrayEquals(new int[]{1148,0},p[1]);
        assertArrayEquals(new int[]{539,1079},p[2]); assertArrayEquals(new int[]{1515,679},p[3]);
        String[] signed=points(); signed[0]="-229"; signed[7]="-400";
        assertTrue(Arrays.deepEquals(p,OrientationMath.hal(signed,1920,1080)));
        int[][] full=OrientationMath.hal(new String[]{"0","0","0","0","0","0","0","0"},1920,1080);
        assertArrayEquals(new int[]{1919,1079},full[3]);
        int[][] maximum=OrientationMath.hal(new String[]{"1919","1079","1919","1079","1919","1079","1919","1079"},1920,1080);
        assertArrayEquals(new int[]{1919,1079},maximum[0]);assertArrayEquals(new int[]{0,0},maximum[3]);
        for(String value:new String[]{"1920","-2147483648","2147483647","1.0"}) {
            String[] bad=points();bad[0]=value;fails(()->OrientationMath.hal(bad,1920,1080));
        }
        fails(()->OrientationMath.hal(points(),0,1080)); fails(()->OrientationMath.hal(points(),1921,1080));
    }
    @Test public void invalidModeGeometryAndZoomGate() throws Exception {
        fails(()->OrientationMath.nativeCommand(4,true,true));
        fails(()->OrientationMath.permute(points(),-1,0));
        for(String z:new String[]{"99.9","NaN","Infinity","","101"}) fails(()->OrientationMath.zoom(z,"100.0"));
        OrientationMath.zoom("100.0","1e2");
        String[] degenerate={"0","0","1919","0","0","0","1919","0"};
        fails(()->OrientationMath.hal(degenerate,1920,1080));
    }
    @Test public void all16TransitionsBothModelsAndRequestedHal() throws Exception {
        for(int old=0;old<4;old++) for(int next=0;next<4;next++) {
            OrientationPilot.State oldState=initial().target(old);
            MemoryJournal j=new MemoryJournal();Fake f=new Fake(j);f.state=oldState;
            OrientationPilot p=new OrientationPilot(f,j,new AtomicBoolean());
            if(old==next) { int mode=next; fails(()->p.run(f.state,mode,false));assertEquals(0,f.attempts);continue; }
            p.run(f.state,next,false);
            assertTrue(oldState.target(next).same(f.state));
            assertTrue(Arrays.deepEquals(OrientationMath.hal(oldState.target(next).display(),1920,1080),f.requestedPoints));
            assertEquals(OrientationMath.horizontal(next),f.flippedH);assertEquals(OrientationMath.vertical(next),f.flippedV);
            assertEquals(22,f.attempts);
        }
    }
    @Test public void applyThenExplicitRestoreOriginalExact19AndSeparateModels() throws Exception {
        MemoryJournal j=new MemoryJournal(); Fake f=new Fake(j);OrientationPilot.State original=f.state;
        OrientationPilot p=new OrientationPilot(f,j,new AtomicBoolean());
        p.run(original,1,false);
        assertTrue(j.records.contains("APPLY_STORED_READBACK_OK_PHYSICAL_UNVERIFIED"));
        assertTrue(j.records.contains("MIRROR_RAW_-1"));
        assertTrue(j.records.contains("MIRROR_" + MirrorReceipt.VERIFIED));
        fails(()->p.run(f.state,2,false)); // Never overwrite/retry an existing original.
        p.run(p.inspectRestore(),0,true);
        assertTrue(original.same(f.state)); assertSame(original,j.original);
        assertTrue(j.records.contains("RESTORE_STORED_READBACK_OK_PHYSICAL_UNVERIFIED"));
        assertEquals("+0229",f.state.value(OrientationPilot.Property.D_LTX));
        assertEquals("1e2",f.state.value(OrientationPilot.Property.ZOOM_V));
    }
    @Test public void restoreAfterDeniedFirstPropertyRestoresHardwareWithoutNoopPropertyWrites() throws Exception {
        MemoryJournal j = new MemoryJournal();
        final int[] denied = {0};
        Fake f = new Fake(j) {
            @Override public void persist(OrientationPilot.Property property, String value) {
                denied[0]++;
                throw new SecurityException("Ordinary-app property writes denied");
            }
        };
        OrientationPilot.State original = f.state;
        OrientationPilot p = new OrientationPilot(f, j, new AtomicBoolean());
        fails(() -> p.run(original, 1, false));
        assertEquals(1, denied[0]);
        assertEquals(1, f.state.mirror);
        assertEquals(0, f.state.mode());
        p.run(p.inspectRestore(), 0, true);
        assertEquals("Restore must not attempt unchanged protected properties", 1, denied[0]);
        assertTrue(original.same(f.state));
        assertFalse(f.flippedH); assertFalse(f.flippedV);
        assertTrue(Arrays.deepEquals(OrientationMath.hal(original.display(), 1920, 1080), f.requestedPoints));
        assertTrue(j.records.contains("RESTORE_UNCHANGED_MODE"));
        assertTrue(j.records.contains("RESTORE_STORED_READBACK_OK_PHYSICAL_UNVERIFIED"));
    }
    @Test public void uncertainFailureAtEveryMutationRetainsOriginalAndNoAutomaticRestore() throws Exception {
        for(int failure=1;failure<=22;failure++) {
            MemoryJournal j=new MemoryJournal();Fake f=new Fake(j);f.failAt=failure;
            OrientationPilot.State original=f.state;OrientationPilot p=new OrientationPilot(f,j,new AtomicBoolean());
            fails(()->p.run(original,1,false));
            assertEquals(failure,f.attempts);assertSame(original,j.original);assertTrue(p.stage.startsWith("UNKNOWN_PARTIAL"));
            f.failAt=0; p.run(p.inspectRestore(),0,true);
            assertTrue(original.same(f.state));assertSame(original,j.original);
        }
    }
    @Test public void nonzeroHalResultsNeverBecomeSuccessEvenIfMirrorReadbackMatches() throws Exception {
        for(int stage:new int[]{1,2,4}) {
            MemoryJournal j=new MemoryJournal();Fake f=new Fake(j);f.returnAt=stage;
            OrientationPilot p=new OrientationPilot(f,j,new AtomicBoolean());
            fails(()->p.run(f.state,1,false)); assertEquals(stage,f.attempts);
            assertFalse(j.records.contains("APPLY_STORED_READBACK_OK_PHYSICAL_UNVERIFIED"));
        }
    }
    @Test public void ignoredPersistenceReadbackAndPostWriteJournalFailureStop() throws Exception {
        for(boolean ignored:new boolean[]{true,false}) {
            MemoryJournal j=new MemoryJournal();Fake f=new Fake(j);f.dropProperty=ignored;f.journalFailureAfterWrite=!ignored;
            OrientationPilot p=new OrientationPilot(f,j,new AtomicBoolean());
            fails(()->p.run(f.state,1,false));assertEquals(3,f.attempts);assertTrue(p.stage.startsWith("UNKNOWN_PARTIAL"));
            assertNotNull(j.original);
        }
    }
    @Test public void explicitRestoreFailureAtEveryStageDoesNotRetryOrReplaceOriginal() throws Exception {
        // This fixture already has the original ZOOM_H encoding: Restore performs
        // three HAL stages and eighteen changed-property writes, not that no-op.
        for(int failure=1;failure<=21;failure++) {
            MemoryJournal j=new MemoryJournal();Fake f=new Fake(j);OrientationPilot.State original=f.state;
            OrientationPilot p=new OrientationPilot(f,j,new AtomicBoolean());p.run(original,1,false);
            f.failAt=22+failure;
            fails(()->p.run(f.state,0,true));assertEquals(22+failure,f.attempts);assertSame(original,j.original);
            assertFalse(j.records.contains("RESTORE_STORED_READBACK_OK_PHYSICAL_UNVERIFIED"));
        }
    }
    @Test public void firmwareRasterAutoAndVectorGuardsAndNativeConsistency() throws Exception {
        for(OrientationPilot.Guard changed:OrientationPilot.Guard.values()) {
            OrientationPilot.State s=initial();
            String[] values=new String[OrientationPilot.Property.values().length];
            String[] guards=new String[OrientationPilot.Guard.values().length];
            for(OrientationPilot.Property p:OrientationPilot.Property.values()) values[p.ordinal()]=s.value(p);
            for(OrientationPilot.Guard g:OrientationPilot.Guard.values()) guards[g.ordinal()]=s.guard(g);
            guards[changed.ordinal()]="unsupported";
            OrientationPilot.State bad=new OrientationPilot.State(values,guards,0);
            fails(()->bad.validate(true));
        }
        fails(()->initial().mirror(1).validate(true));
        initial().mirror(1).validate(false); // A fresh explicit partial-state restore review is separate.
    }
    @Test public void cancelBeforeAndBetweenEveryStageHasBoundedWrites() throws Exception {
        MemoryJournal j=new MemoryJournal();Fake f=new Fake(j);
        OrientationPilot p=new OrientationPilot(f,j,new AtomicBoolean(true));
        fails(()->p.run(f.state,1,false));assertEquals(0,f.attempts);assertNull(j.original);
        for(int stop=1;stop<=22;stop++) {
            MemoryJournal log=new MemoryJournal();Fake backend=new Fake(log);AtomicBoolean cancel=new AtomicBoolean();
            backend.cancel=cancel;backend.cancelAt=stop;
            OrientationPilot controller=new OrientationPilot(backend,log,cancel);
            fails(()->controller.run(backend.state,1,false));assertEquals(stop,backend.attempts);
        }
    }
    @Test public void stableReadsAreNotALockDriftStopsBeforeNextWrite() throws Exception {
        for(int drift:new int[]{2,3,4,5,6,12,30,46}) {
            MemoryJournal j=new MemoryJournal();Fake f=new Fake(j);f.driftAt=drift;
            OrientationPilot p=new OrientationPilot(f,j,new AtomicBoolean());
            fails(()->p.run(f.state,1,false));assertTrue(f.attempts<=22);
            assertFalse(j.records.contains("APPLY_STORED_READBACK_OK_PHYSICAL_UNVERIFIED"));
        }
    }
    @Test public void staleSelectionAndMissingRequiredKeysStopBeforeMutation() throws Exception {
        MemoryJournal j=new MemoryJournal();Fake f=new Fake(j);OrientationPilot p=new OrientationPilot(f,j,new AtomicBoolean());
        OrientationPilot.State capture=p.inspect();f.state=f.state.property(OrientationPilot.Property.H_LTX,"99");
        fails(()->p.run(capture,1,false));assertEquals(0,f.attempts);
        for(OrientationPilot.Property prop:OrientationPilot.Property.values()) {
            f.state=initial().property(prop,"");fails(p::inspect);assertEquals(0,f.attempts);
        }
    }
    @Test public void normalWriteContractGateStopsBeforeFlipAndBackup() throws Exception {
        MemoryJournal j=new MemoryJournal();Fake f=new Fake(j);f.gate=true;
        OrientationPilot p=new OrientationPilot(f,j,new AtomicBoolean());
        fails(()->p.run(f.state,1,false));assertEquals(0,f.attempts);assertNull(j.original);
        fails(()->new OrientationVendorBackend(true).prepareWrite());
    }
    @Test public void journalFailureBeforeMutationStopsWithoutRetry() throws Exception {
        for(boolean creation:new boolean[]{true,false}) {
            MemoryJournal j=new MemoryJournal();Fake f=new Fake(j);j.failCreate=creation;j.failRecord=!creation;
            OrientationPilot p=new OrientationPilot(f,j,new AtomicBoolean());
            fails(()->p.run(f.state,1,false));assertEquals(0,f.attempts);
        }
    }
    @Test public void actualProductionGateStopsApplyAndExplicitRestoreZeroBeforeEveryMutation() throws Exception {
        for (boolean restore : new boolean[]{false, true}) {
            MemoryJournal j = new MemoryJournal();
            OrientationPilot.State original = initial();
            if (restore) j.original = original;
            Fake f = new Fake(j) {
                @Override public void prepareWrite() throws Exception { new OrientationVendorBackend(true).prepareWrite(); }
            };
            if (restore) f.state = original.target(1);
            OrientationPilot.State current = f.state;
            OrientationPilot p = new OrientationPilot(f, j, new AtomicBoolean());
            fails(() -> p.run(current, restore ? 0 : 1, restore));
            assertEquals(0, f.attempts);
            assertTrue(current.same(f.state));
            assertTrue(p.stage.startsWith("STOPPED_BEFORE_WRITE"));
            assertFalse(j.records.contains("BEFORE_FLIP"));
            if (restore) assertSame(original, j.original);
            else { assertNull(j.original); assertTrue(j.records.isEmpty()); }
        }
    }
    @Test public void actualProductionGateNeverInfersReadinessFromMatchingDefaultZero() throws Exception {
        for (int i = 0; i < 2; i++) {
            try {
                new OrientationVendorBackend(true).prepareWrite();
                fail("No receipt may be inferred from matching zero/default values");
            } catch (IllegalStateException blocked) {
                assertTrue(blocked.getMessage().contains("writer disabled"));
                assertTrue(blocked.getMessage().contains("present-field"));
                assertTrue(blocked.getMessage().contains("before any flip"));
            }
        }
    }
    @Test public void missingFieldAndWrongPathReadinessStopBeforeFirstFlip() throws Exception {
        for (MirrorField.Value panel : new MirrorField.Value[]{null,
                new MirrorField.Value(MirrorField.Path.RESERVE, 0, "0".repeat(64))}) {
            MemoryJournal j=new MemoryJournal();Fake f=new Fake(j);
            OrientationPilot.State s=f.state;
            String[] values=new String[OrientationPilot.Property.values().length];
            String[] guards=new String[OrientationPilot.Guard.values().length];
            for(OrientationPilot.Property p:OrientationPilot.Property.values()) values[p.ordinal()]=s.value(p);
            for(OrientationPilot.Guard g:OrientationPilot.Guard.values()) guards[g.ordinal()]=s.guard(g);
            f.state=new OrientationPilot.State(values,guards,0,panel);
            OrientationPilot controller=new OrientationPilot(f,j,new AtomicBoolean());
            fails(()->controller.run(f.state,1,false));assertEquals(0,f.attempts);assertNull(j.original);
        }
    }
    @Test public void restoreRefusesChangedOriginalPanelSurroundingsBeforeMutation() throws Exception {
        MemoryJournal j=new MemoryJournal();Fake f=new Fake(j);
        OrientationPilot controller=new OrientationPilot(f,j,new AtomicBoolean());controller.run(f.state,1,false);
        OrientationPilot.State s=f.state;
        String[] values=new String[OrientationPilot.Property.values().length];
        String[] guards=new String[OrientationPilot.Guard.values().length];
        for(OrientationPilot.Property p:OrientationPilot.Property.values()) values[p.ordinal()]=s.value(p);
        for(OrientationPilot.Guard g:OrientationPilot.Guard.values()) guards[g.ordinal()]=s.guard(g);
        f.state=new OrientationPilot.State(values,guards,s.mirror,new MirrorField.Value(MirrorField.Path.OEM,1,"1".repeat(64)));
        fails(()->controller.run(f.state,0,true));assertEquals(22,f.attempts);
    }
    @Test public void rawMirrorResponseIsJournaledEvenWhenReadbackTransportFails() throws Exception {
        MemoryJournal j=new MemoryJournal();Fake f=new Fake(j) {
            @Override public MirrorReceipt mirror(int mode) throws Exception {
                MirrorReceipt receipt=super.mirror(mode);
                receipt.readFailure=new Exception("getter transport failed");return receipt;
            }
        };
        OrientationPilot controller=new OrientationPilot(f,j,new AtomicBoolean());
        fails(()->controller.run(f.state,1,false));assertEquals(2,f.attempts);
        assertTrue(j.records.contains("MIRROR_RAW_-1"));assertTrue(controller.stage.startsWith("UNKNOWN_PARTIAL"));
        assertEquals(0,j.original.panel.stored);
    }
    @Test public void workerLockRemainsHeldUntilUncancellableCallReturns() throws Exception {
        MemoryJournal j=new MemoryJournal();Fake f=new Fake(j);f.entered=new CountDownLatch(1);f.release=new CountDownLatch(1);
        AtomicBoolean cancelled=new AtomicBoolean();OrientationPilot p=new OrientationPilot(f,j,cancelled);
        List<Exception> failures=new ArrayList<>();
        Thread worker=new Thread(()->{try{p.run(f.state,1,false);}catch(Exception e){failures.add(e);}});
        worker.start();assertTrue(f.entered.await(2,TimeUnit.SECONDS));
        try {
            cancelled.set(true);assertTrue(OrientationPilot.busy());
            OrientationPilot other=new OrientationPilot(new Fake(new MemoryJournal()),new MemoryJournal(),new AtomicBoolean());
            fails(other::inspect);assertTrue(OrientationPilot.busy());assertEquals(1,f.attempts);
        } finally { f.release.countDown();worker.join(3000); }
        assertFalse(worker.isAlive());assertFalse(OrientationPilot.busy());assertEquals(1,f.attempts);assertEquals(1,failures.size());
    }
    @Test public void durableOriginalSurvivesReloadAndRefusesOverwriteOrTruncation() throws Exception {
        File root=temporary.newFolder();OrientationJournal journal=new OrientationJournal(root);
        journal.create(initial());journal.record("BEFORE_FLIP",initial());
        OrientationJournal reloaded=new OrientationJournal(root);assertTrue(initial().same(reloaded.original()));
        fails(()->reloaded.create(initial().target(1)));assertTrue(initial().same(reloaded.original()));
        try(FileOutputStream corrupt=new FileOutputStream(new File(root,"orientation-pilot/original.bin"))) { corrupt.write(0); }
        assertTrue(reloaded.exists());fails(reloaded::original);fails(()->reloaded.create(initial()));
    }
    @Test public void completedJournalArchivesAndAllowsRepeatedChanges() throws Exception {
        File root=temporary.newFolder(); OrientationJournal j=new OrientationJournal(root);
        for(int i=0;i<3;i++) {
            OrientationPilot.State before=initial(), after=before.target(1);
            j.create(before); j.record("APPLY_STORED_READBACK_OK_PHYSICAL_UNVERIFIED",after);
            j.complete(after); assertFalse(j.exists());
        }
        File[] archives=new File(root,"orientation-history").listFiles();
        assertNotNull(archives);assertEquals(3,archives.length);
        for(File archive:archives) assertTrue(new File(archive,"original.bin").isFile());
    }
    @Test public void journalCannotCompleteFailedOrChangedState() throws Exception {
        OrientationJournal j=new OrientationJournal(temporary.newFolder());j.create(initial());
        j.record("UNKNOWN_PARTIAL",initial());fails(()->j.complete(initial()));assertTrue(j.exists());
        j.record("RESTORE_STORED_READBACK_OK_PHYSICAL_UNVERIFIED",initial());
        fails(()->j.complete(initial().target(1)));assertTrue(j.exists());
        j.complete(initial());assertFalse(j.exists());
    }
    @Test public void truncatedJournalCannotBeArchived() throws Exception {
        File root=temporary.newFolder();OrientationJournal j=new OrientationJournal(root);j.create(initial());
        j.record("RESTORE_STORED_READBACK_OK_PHYSICAL_UNVERIFIED",initial());
        try(FileOutputStream out=new FileOutputStream(new File(root,"orientation-pilot/events.bin"),true)) { out.write(0); }
        fails(()->j.complete(initial()));assertTrue(j.exists());assertTrue(initial().same(j.original()));
    }
    @Test public void acknowledgementBeforeRenameCanBeRetried() throws Exception {
        OrientationJournal j=new OrientationJournal(temporary.newFolder());j.create(initial());
        j.record("OWNER_CONFIRMED_PICTURE",initial());j.complete(initial());assertFalse(j.exists());
    }
    @Test public void staleCompletionCannotArchiveOrMutate() throws Exception {
        MemoryJournal j=new MemoryJournal();Fake f=new Fake(j);
        OrientationPilot p=new OrientationPilot(f,j,new AtomicBoolean());
        OrientationPilot.State reviewed=f.state;f.state=f.state.target(1);
        fails(()->p.complete(reviewed));assertEquals(0,f.attempts);assertFalse(OrientationPilot.busy());
    }
    @Test public void invalidOrientationModesAreRejectedBeforeMutation() throws Exception {
        for(int mode:new int[]{-1,4}) {
            MemoryJournal j=new MemoryJournal();Fake f=new Fake(j);OrientationPilot p=new OrientationPilot(f,j,new AtomicBoolean());
            fails(()->p.run(f.state,mode,false));assertEquals(0,f.attempts);assertFalse(j.exists());
        }
    }
    @Test public void manualRecoveryConflictBlocksOrientationApplyAndRestore()throws Exception{
        MemoryJournal j=new MemoryJournal(){@Override public boolean conflicts(){return true;}};
        Fake f=new Fake(j);OrientationPilot p=new OrientationPilot(f,j,new AtomicBoolean());
        fails(()->p.run(f.state,1,false));assertEquals(0,f.attempts);assertFalse(j.exists());
        j.original=f.state;fails(()->p.run(f.state,0,true));assertEquals(0,f.attempts);assertTrue(j.exists());
    }
}
