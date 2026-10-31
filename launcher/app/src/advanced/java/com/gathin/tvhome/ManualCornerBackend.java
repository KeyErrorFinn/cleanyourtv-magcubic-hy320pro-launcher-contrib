package com.gathin.tvhome;

/** Typed manual correction backend. No orientation, camera or arbitrary property operations. */
final class ManualCornerBackend implements ManualCornerTransaction.Backend {
    private static final boolean LIVE_MANUAL_WRITER = true;
    private final boolean writeEnabled;
    ManualCornerBackend() { this(false); }
    ManualCornerBackend(boolean forceReadOnly) { writeEnabled = LIVE_MANUAL_WRITER && !forceReadOnly; }
    static boolean writesEnabled() { return LIVE_MANUAL_WRITER; }
    private final OrientationVendorBackend backend = new OrientationVendorBackend();
    @Override public void resolve() throws Exception { ManualCornerLimits.read(); backend.resolve(); }
    @Override public OrientationPilot.State read() throws Exception {
        ManualCornerLimits.read();OrientationPilot.State state=backend.read();
        ProjectionScaleBackend.manualEnvironment();
        return state;
    }
    @Override public void prepareWrite() throws Exception {
        if (!writeEnabled) throw new IllegalStateException("Manual-corner writer disabled");
        backend.prepareWrite();
    }
    static void validateCoordinates(int[][] points) {
        if (points == null || points.length != 4) throw new IllegalArgumentException("Four corners required");
        String[] offsets = new String[8];
        for (int i = 0; i < 4; i++) {
            if (points[i] == null || points[i].length != 2 || points[i][0] < 0 || points[i][0] > 1919
                    || points[i][1] < 0 || points[i][1] > 1079) throw new IllegalArgumentException("Corner outside raster");
            offsets[2*i] = Integer.toString(i % 2 == 0 ? points[i][0] : 1919-points[i][0]);
            offsets[2*i+1] = Integer.toString(i / 2 == 0 ? points[i][1] : 1079-points[i][1]);
        }
        ManualCornerModel.validateOffsets(offsets,1000,1000);
    }
    @Override public int coordinates(int[][] points) throws Exception { prepareWrite(); validateCoordinates(points); return backend.coordinates(points); }
    @Override public void persist(OrientationPilot.Property property, String value) throws Exception {
        prepareWrite();
        if (property.ordinal() < 3 || property.ordinal() >= 11) throw new IllegalArgumentException("Not a display corner");
        int number = Integer.parseInt(value);
        if (number < 0 || number > 1000) throw new IllegalArgumentException("Corner offset outside reviewed limit");
        backend.persist(property,value);
    }
}
