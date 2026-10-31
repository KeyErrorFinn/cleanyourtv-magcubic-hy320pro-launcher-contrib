package com.gathin.tvhome;

import java.io.IOException;
import java.util.Arrays;

/** Read-only driver parameters, distinct from saved properties and physical optical proof. */
final class DriverKeystoneState {
    private static boolean loaded;
    private static native int[] nativeRead();
    static synchronized int[] read() throws IOException {
        if (!"Projector_20260713.153921".equals(android.os.Build.ID)
                || android.os.Build.VERSION.SDK_INT != 34 || android.os.Process.is64Bit()
                || android.os.Process.myUid() / 100000 != 0 || android.os.Process.myUid() % 100000 < 10000)
            throw new IOException("Reviewed ordinary-app ARM32 identity required");
        if (!loaded) { System.loadLibrary("cytv_ksc_read"); loaded = true; }
        int[] raw = nativeRead();
        if (raw == null || raw.length != 10 || raw[0] != 0)
            throw new IOException("Driver getter unavailable" + (raw != null && raw.length == 10 ? " (errno " + raw[1] + ")" : ""));
        int[] points = Arrays.copyOfRange(raw, 2, 10);
        for (int i = 0; i < points.length; i++)
            if (points[i] < 0 || points[i] >= (i % 2 == 0 ? 1920 : 1080))
                throw new IOException("Driver points outside reviewed raster");
        return points;
    }
    static boolean matches(OrientationPilot.State state, int[] points) {
        int[][] expected = OrientationMath.hal(state.display(), 1920, 1080);
        int[] flat = new int[8];
        for (int i = 0; i < 4; i++) { flat[i * 2] = expected[i][0]; flat[i * 2 + 1] = expected[i][1]; }
        return Arrays.equals(flat, points);
    }
    private DriverKeystoneState() { }
}
