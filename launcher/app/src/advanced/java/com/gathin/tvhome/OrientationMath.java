package com.gathin.tvhome;

/** Independently reconstructed corner-label math. Order is always LT, RT, LB, RB. */
final class OrientationMath {
    private OrientationMath() { }
    static void mode(int mode) {
        if (mode < 0 || mode > 3) throw new IllegalArgumentException("Logical mode must be 0..3");
    }
    static boolean horizontal(int mode) { mode(mode); return mode == 1 || mode == 2; }
    static boolean vertical(int mode) { mode(mode); return mode == 2 || mode == 3; }
    static int nativeBits(int mode) {
        return (horizontal(mode) ? 1 : 0) | (vertical(mode) ? 2 : 0);
    }
    static int nativeCommand(int mode, boolean optics, boolean displayAvailable) {
        mode(mode);
        return optics && displayAvailable ? 8 | nativeBits(mode) : mode;
    }
    static String[] permute(String[] pairs, int oldMode, int newMode) {
        if (pairs.length != 8) throw new IllegalArgumentException("Four intact XY pairs required");
        boolean h = horizontal(oldMode) != horizontal(newMode);
        boolean v = vertical(oldMode) != vertical(newMode);
        String[] out = new String[8];
        for (int source = 0; source < 4; source++) {
            int destination = source ^ (h ? 1 : 0) ^ (v ? 2 : 0);
            out[2 * destination] = pairs[2 * source];
            out[2 * destination + 1] = pairs[2 * source + 1];
        }
        return out;
    }
    static void zoom(String horizontal, String vertical) {
        if (Float.parseFloat(horizontal) != 100f || Float.parseFloat(vertical) != 100f)
            throw new IllegalArgumentException("Only stored zoom 100/100 is reviewed");
    }
    /** Conservative convexity/bounds policy, not a complete hardware-safe envelope. */
    static int[][] hal(String[] offsets, int width, int height) {
        if (offsets.length != 8 || width < 2 || height < 2 || width > 1920 || height > 1080)
            throw new IllegalArgumentException("Unsupported raster/geometry");
        int[][] points = new int[4][2];
        for (int i = 0; i < 4; i++) {
            for (int axis = 0; axis < 2; axis++) {
                int value = Integer.parseInt(offsets[2 * i + axis]);
                if (value == Integer.MIN_VALUE) throw new IllegalArgumentException("Offset abs overflow");
                int magnitude = Math.abs(value), maximum = (axis == 0 ? width : height) - 1;
                if (magnitude > maximum) throw new IllegalArgumentException("Offset outside raster");
                points[i][axis] = ((axis == 0 ? i % 2 : i / 2) == 0) ? magnitude : maximum - magnitude;
            }
        }
        int[] winding = {0, 1, 3, 2};
        for (int i = 0; i < 4; i++) {
            int[] a = points[winding[i]], b = points[winding[(i + 1) % 4]], c = points[winding[(i + 2) % 4]];
            long cross = (long) (b[0] - a[0]) * (c[1] - b[1]) - (long) (b[1] - a[1]) * (c[0] - b[0]);
            if (cross <= 0) throw new IllegalArgumentException("Crossed/concave/degenerate geometry");
        }
        return points;
    }
}
