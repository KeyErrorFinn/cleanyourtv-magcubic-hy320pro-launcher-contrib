package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class StandardPreferenceValuesTest {
    @Test public void permitsOnlyReviewedPreferenceValues() {
        assertTrue(StandardPreferenceValues.allowed("sound_effects_enabled", "0"));
        assertTrue(StandardPreferenceValues.allowed("sound_effects_enabled", "1"));
        assertTrue(StandardPreferenceValues.allowed("time_12_24", "12"));
        assertTrue(StandardPreferenceValues.allowed("time_12_24", "24"));
        for (String value : new String[] {"300000", "600000", "1200000", "3600000", "2147483647"})
            assertTrue(StandardPreferenceValues.allowed("screen_off_timeout", value));
    }
    @Test public void rejectsOtherNamespacesAndUnsafeValues() {
        for (String key : new String[] {"adb_enabled", "auto_time", "bluetooth_on", "screen_brightness", "persist.sys.zoom_value", "", null})
            assertFalse(StandardPreferenceValues.allowed(key, "1"));
        for (String value : new String[] {"-1", "0", "1", "300", "2147483648", "", null})
            assertFalse(StandardPreferenceValues.allowed("screen_off_timeout", value));
        assertFalse(StandardPreferenceValues.allowed("time_12_24", "0"));
        assertFalse(StandardPreferenceValues.allowed("sound_effects_enabled", "24"));
    }
}
