package com.gathin.tvhome;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Fixed normal-access transport only. No access exemptions, brokers, alternate APIs or selector input. */
final class OrientationVendorBackend implements OrientationPilot.Backend {
    // Enabled for this verified firmware; all calls remain guarded by snapshots and recovery.
    private static final boolean LIVE_WRITER_ENABLED = true;
    static boolean writesEnabled() { return LIVE_WRITER_ENABLED; }
    private final boolean writerEnabled;
    OrientationVendorBackend() { this(false); }
    // Tests can further restrict transport, never enable a disabled release writer.
    OrientationVendorBackend(boolean disableWrites) { writerEnabled = LIVE_WRITER_ENABLED && !disableWrites; }
    private static final String DISPLAY = "vendor.display.config@1.0::IDisplayConfig";
    private static final String TV = "vendor.aw.homlet.tvsystem.tvserver@1.0::ITvServer";
    private static final String BASE = "android.hidl.base@1.0::IBase";
    private enum Call { DESCRIPTOR, MIRROR_READ, MIRROR_WRITE, FLIP, COORDINATES }
    private Constructor<?> parcel, blob;
    private Method get, set, token, integer, bool, floating, buffer, putInt, transact, success, readInt,
            readString, temporary, release;
    private Object display, tv;

    @Override public void resolve() throws Exception {
        if (!"Projector_20260713.153921".equals(android.os.Build.ID)
                || android.os.Build.VERSION.SDK_INT != 34 || android.os.Process.myUid() / 100000 != 0
                || android.os.Process.myUid() % 100000 < 10000)
            throw new IllegalStateException("Reviewed ordinary user0 firmware identity required");
        verifyNativeIdentity();
        if (display != null && tv != null) return;
        Class<?> props = Class.forName("android.os.SystemProperties");
        get = props.getMethod("get", String.class, String.class);
        set = props.getMethod("set", String.class, String.class); // Lookup only; never a setter probe.
        Class<?> binderClass = Class.forName("android.os.HwBinder");
        Class<?> binderInterface = Class.forName("android.os.IHwBinder");
        Class<?> parcelClass = Class.forName("android.os.HwParcel");
        Class<?> blobClass = Class.forName("android.os.HwBlob");
        Method service = binderClass.getMethod("getService", String.class, String.class, boolean.class);
        parcel = parcelClass.getConstructor(); blob = blobClass.getConstructor(int.class);
        token = parcelClass.getMethod("writeInterfaceToken", String.class);
        integer = parcelClass.getMethod("writeInt32", int.class);
        bool = parcelClass.getMethod("writeBool", boolean.class);
        floating = parcelClass.getMethod("writeFloat", float.class);
        buffer = parcelClass.getMethod("writeBuffer", blobClass);
        putInt = blobClass.getMethod("putInt32", long.class, int.class);
        transact = binderInterface.getMethod("transact", int.class, parcelClass, parcelClass, int.class);
        success = parcelClass.getMethod("verifySuccess"); readInt = parcelClass.getMethod("readInt32");
        readString = parcelClass.getMethod("readString");
        temporary = parcelClass.getMethod("releaseTemporaryStorage"); release = parcelClass.getMethod("release");
        Object d = service.invoke(null, DISPLAY, "default", false);
        Object t = service.invoke(null, TV, "default", false);
        if (d == null || t == null) throw new IllegalStateException("Required exact vendor service unavailable");
        if (!DISPLAY.equals(call(d, DISPLAY, Call.DESCRIPTOR, 0, null))
                || !TV.equals(call(t, TV, Call.DESCRIPTOR, 0, null)))
            throw new IllegalStateException("Vendor descriptor mismatch");
        display = d; tv = t;
    }
    @Override public OrientationPilot.State read() throws Exception {
        String[] values = new String[OrientationPilot.Property.values().length];
        for (OrientationPilot.Property p : OrientationPilot.Property.values())
            values[p.ordinal()] = (String) get.invoke(null, p.key, "");
        String[] guards = new String[OrientationPilot.Guard.values().length];
        for (OrientationPilot.Guard g : OrientationPilot.Guard.values())
            guards[g.ordinal()] = (String) get.invoke(null, g.key, "");
        MirrorField.Value field = MirrorField.read(MirrorField.FILES);
        int mirror = (Integer) call(tv, TV, Call.MIRROR_READ, 0, null);
        if (!field.same(MirrorField.read(MirrorField.FILES))) throw new IllegalStateException("Stale panel read");
        return new OrientationPilot.State(values, guards, mirror, field);
    }
    @Override public void prepareWrite() throws Exception {
        if (!writerEnabled) throw new IllegalStateException("Live orientation writer disabled; reviewed typed receipt requires present-field readiness before any flip");
        if(get!=null&&!"0".equals(get.invoke(null,"hotack.vaf.runend","")))throw new IllegalStateException("Manufacturer calibration activity is reported; wait before geometry writes");
        if(get!=null)ProjectionScaleBackend.manualEnvironment();
        // Future attended enablement must retain resolve/read validation, exact binaries and all guards.
    }
    @Override public int flip(boolean h, boolean v) throws Exception {
        prepareWrite();
        return (Integer) call(display, DISPLAY, Call.FLIP, (h ? 1 : 0) | (v ? 2 : 0), null);
    }
    @Override public MirrorReceipt mirror(int logicalMode) throws Exception {
        prepareWrite();
        MirrorField.Value before = MirrorField.read(MirrorField.FILES);
        int command = OrientationMath.nativeCommand(logicalMode, true, true);
        int raw = (Integer) call(tv, TV, Call.MIRROR_WRITE, command, null);
        try {
            MirrorField.Value after = MirrorField.read(MirrorField.FILES);
            int getter = (Integer) call(tv, TV, Call.MIRROR_READ, 0, null);
            if (!after.same(MirrorField.read(MirrorField.FILES))) throw new IllegalStateException("Stale receipt field");
            return new MirrorReceipt(raw, command, before, after, getter, android.os.Build.ID);
        } catch (Exception failure) {
            // Keep the known raw response even when independent verification fails.
            MirrorReceipt failed = new MirrorReceipt(raw, command, before, null, -1, android.os.Build.ID);
            failed.readFailure = failure;
            return failed;
        }
    }
    @Override public int coordinates(int[][] points) throws Exception {
        prepareWrite();
        if (points.length != 4) throw new IllegalArgumentException("Four points required");
        for (int[] p : points) if (p.length != 2 || p[0] < 0 || p[0] >= 1920 || p[1] < 0 || p[1] >= 1080)
            throw new IllegalArgumentException("Point outside reviewed raster");
        return (Integer) call(display, DISPLAY, Call.COORDINATES, 0, points);
    }
    @Override public void persist(OrientationPilot.Property property, String encoding) throws Exception {
        prepareWrite();
        if (encoding == null || encoding.isEmpty()) throw new IllegalArgumentException("No absent-property writes");
        if (property == OrientationPilot.Property.ZOOM_H || property == OrientationPilot.Property.ZOOM_V)
            OrientationMath.zoom(encoding, encoding);
        else { int value = Integer.parseInt(encoding); if (property == OrientationPilot.Property.MODE) OrientationMath.mode(value); }
        set.invoke(null, property.key, encoding);
    }
    private static void verifyNativeIdentity() throws Exception {
        String[][] reviewed = {
            {"/vendor/bin/hw/tvserver", "da879264d3371be4de1d21dce41a98eccd6861f82bb2dcecd80074d8579f3df4"},
            {"/vendor/lib/vendor.aw.homlet.tvsystem.tvserver@1.0.so", "13bf540e594d54804dff00118836c4f80005f3fdb3a0101780b5a5949788fa04"},
            {"/vendor/lib/libtvpq.so", "285604b7dde915519831f4a582b43d5e487973b29773e9d8be1a1e0244136823"},
            {"/vendor/lib/libminIni.so", "00768b46b27e0d64250d813db49ac5e50e23c80bef1313acc5e811bd0e7fb5a4"}
        };
        for (String[] item : reviewed) {
            java.security.MessageDigest hash = java.security.MessageDigest.getInstance("SHA-256");
            try (java.io.FileInputStream in = new java.io.FileInputStream(item[0])) {
                byte[] buffer = new byte[4096]; int count, total = 0;
                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    if (total > 1048576) throw new IllegalStateException("Native identity bound exceeded");
                    hash.update(buffer, 0, count);
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : hash.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            if (!item[1].equals(hex.toString())) throw new IllegalStateException("Unreviewed native binary");
        }
    }
    private Object call(Object binder, String descriptor, Call call, int bits, int[][] points) throws Exception {
        Object request = parcel.newInstance();
        Object reply = null;
        try {
            reply = parcel.newInstance();
            token.invoke(request, call == Call.DESCRIPTOR ? BASE : descriptor);
            int transaction;
            switch (call) {
                case DESCRIPTOR: transaction = 256136003; break;
                case MIRROR_READ: transaction = 138; integer.invoke(request, 3); break;
                case MIRROR_WRITE:
                    transaction = 137; integer.invoke(request, 3); integer.invoke(request, bits); break;
                case FLIP:
                    transaction = 39; bool.invoke(request, (bits & 1) != 0); bool.invoke(request, (bits & 2) != 0); break;
                case COORDINATES:
                    transaction = 36; floating.invoke(request, 100f); floating.invoke(request, 100f);
                    // Four separate 8-byte HIDL KscPoint blobs: X at0, Y at4; LT, RT, LB, RB.
                    for (int[] point : points) {
                        Object pair = blob.newInstance(8);
                        putInt.invoke(pair, 0L, point[0]); putInt.invoke(pair, 4L, point[1]);
                        buffer.invoke(request, pair);
                    }
                    break;
                default: throw new IllegalStateException("Unreachable operation");
            }
            transact.invoke(binder, transaction, request, reply, 0);
            success.invoke(reply); temporary.invoke(request);
            return call == Call.DESCRIPTOR ? readString.invoke(reply) : readInt.invoke(reply);
        } finally {
            // Release failures also propagate. Never convert transport failure into successful state.
            try { if (reply != null) release.invoke(reply); } finally { release.invoke(request); }
        }
    }
}
