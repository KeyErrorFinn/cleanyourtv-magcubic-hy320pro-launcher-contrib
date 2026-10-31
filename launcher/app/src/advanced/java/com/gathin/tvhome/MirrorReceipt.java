package com.gathin.tvhome;

/** Stored-field receipt for ONE reviewed bit8 branch; never certifies physical orientation. */
final class MirrorReceipt {
    static final String VERIFIED = "STORED_READBACK_VERIFIED_PHYSICAL_UNVERIFIED";
    final int rawCode, command, nativeGetter;
    final MirrorField.Value before, after;
    final String firmware;
    Exception readFailure;
    MirrorReceipt(int rawCode, int command, MirrorField.Value before, MirrorField.Value after,
                  int nativeGetter, String firmware) {
        this.rawCode = rawCode; this.command = command; this.before = before; this.after = after;
        this.nativeGetter = nativeGetter; this.firmware = firmware;
    }
    void verify(int logicalMode, MirrorField.Value originalField) {
        if (readFailure != null) throw new IllegalStateException("Mirror raw=" + rawCode + "; readback failed", readFailure);
        int bits = OrientationMath.nativeBits(logicalMode);
        if (rawCode != -1 || command != OrientationMath.nativeCommand(logicalMode, true, true)
                || !"Projector_20260713.153921".equals(firmware)
                || before == null || after == null || originalField == null
                || before.path != MirrorField.Path.OEM || !before.same(originalField)
                || !after.same(before.mode(bits)) || nativeGetter != bits)
            throw new IllegalStateException("Mirror receipt rejected: raw=" + rawCode + "; outcome unverified");
    }
}
