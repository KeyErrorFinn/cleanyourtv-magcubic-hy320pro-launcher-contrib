package com.gathin.tvhome;

/** In-memory Front/manual-mode editor. This class has no hardware or persistence API. */
final class ManualCornerModel {
    final OrientationPilot.State original;
    private OrientationPilot.State staged;
    private final int widthLimit, heightLimit;
    ManualCornerModel(OrientationPilot.State original, int widthLimit, int heightLimit) {
        original.validateManual(true);
        if (original.mode() != 0) throw new IllegalArgumentException("Preview supports Front projection only");
        if (widthLimit != 1000 || heightLimit != 1000)
            throw new IllegalArgumentException("Unreviewed manual-corner limits");
        this.original = original; this.staged = original;
        this.widthLimit = widthLimit; this.heightLimit = heightLimit;
        validate(original);
    }
    private void validate(OrientationPilot.State state) {
        validateOffsets(state.display(), widthLimit, heightLimit);
    }
    static void validateOffsets(String[] values, int widthLimit, int heightLimit) {
        if (values.length != 8 || widthLimit != 1000 || heightLimit != 1000)
            throw new IllegalArgumentException("Unreviewed corner shape or limits");
        for (String value : values) if (Integer.parseInt(value) < 0)
            throw new IllegalArgumentException("Negative manual offset unsupported");
        for (int corner = 0; corner < 4; corner++) {
            if ((long) Integer.parseInt(values[corner * 2]) + Integer.parseInt(values[(corner ^ 1) * 2]) > widthLimit
                    || (long) Integer.parseInt(values[corner * 2 + 1]) + Integer.parseInt(values[(corner ^ 2) * 2 + 1]) > heightLimit)
                throw new IllegalArgumentException("Existing offsets exceed configured paired limits");
        }
        OrientationMath.hal(values, 1920, 1080);
    }
    int value(int corner, int axis) {
        index(corner, axis); return Integer.parseInt(staged.display()[corner * 2 + axis]);
    }
    private static void index(int corner, int axis) {
        if (corner < 0 || corner > 3 || axis < 0 || axis > 1)
            throw new IllegalArgumentException("Unknown corner/axis");
    }
    static int clockwise(int corner) {
        index(corner,0); return new int[]{1,3,0,2}[corner];
    }
    /** Physical screen direction: 0 left, 1 right, 2 up, 3 down. */
    static int offsetDirection(int corner,int direction) {
        index(corner,0);
        if(direction<0 || direction>3)throw new IllegalArgumentException("Unknown direction");
        int sign=direction==1 || direction==3?1:-1;
        return (direction<2 && corner%2==1 || direction>=2 && corner>=2)?-sign:sign;
    }
    boolean canMove(int corner,int direction) {
        ManualCornerModel probe=new ManualCornerModel(staged,1000,1000);
        try{return probe.step(corner,direction<2?0:1,offsetDirection(corner,direction));}
        catch(IllegalArgumentException blocked){return false;}
    }
    boolean step(int corner, int axis, int delta) {
        index(corner, axis);
        if ((Math.abs(delta) < 1 || Math.abs(delta) > 8) && Math.abs(delta) != 10 && Math.abs(delta) != 25 && Math.abs(delta) != 50 && Math.abs(delta) != 100)
            throw new IllegalArgumentException("Unsupported adjustment step");
        int opponent = corner ^ (axis == 0 ? 1 : 2);
        int maximum = (axis == 0 ? widthLimit : heightLimit) - value(opponent, axis);
        int value = Math.max(0, Math.min(value(corner, axis) + delta, maximum));
        if (value == value(corner, axis)) return false;
        OrientationPilot.Property property = OrientationPilot.Property.values()[3 + corner * 2 + axis];
        String encoding = value == Integer.parseInt(original.value(property)) ? original.value(property) : Integer.toString(value);
        OrientationPilot.State candidate = staged.property(property, encoding);
        validate(candidate); staged = candidate; return true;
    }
    OrientationPilot.State staged() { return staged; }
    int[][] points() { return OrientationMath.hal(staged.display(), 1920, 1080); }
    boolean changed() { return !original.same(staged); }
    void discard() { staged = original; }
    void loadOffsets(String[] offsets) {
        if(offsets.length!=8)throw new IllegalArgumentException("Four corner pairs required");
        OrientationPilot.State candidate=original;
        for(int i=0;i<8;i++) {
            OrientationPilot.Property p=OrientationPilot.Property.values()[3+i];
            int value=Integer.parseInt(offsets[i]);
            candidate=candidate.property(p,value==Integer.parseInt(original.value(p))?original.value(p):offsets[i]);
        }
        validate(candidate);staged=candidate;
    }
    static String describeChange(OrientationPilot.State before, OrientationPilot.State after) {
        String[] names = {"Top left", "Top right", "Bottom left", "Bottom right"};
        StringBuilder description = new StringBuilder();
        for (int i=0;i<8;i++) {
            OrientationPilot.Property p=OrientationPilot.Property.values()[3+i];
            if (!before.value(p).equals(after.value(p))) {
                if(description.length()>0)description.append('\n');
                description.append(names[i/2]).append(i%2==0?" X: ":" Y: ")
                        .append(before.value(p)).append(" → ").append(after.value(p));
            }
        }
        return description.toString();
    }
}
