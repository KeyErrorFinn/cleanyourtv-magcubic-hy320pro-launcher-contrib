package com.gathin.tvhome;

/** The owner-approved WRITE_SETTINGS boundary. No arbitrary key/value writes. */
final class StandardPreferenceValues {
    private StandardPreferenceValues() {}
    static boolean allowed(String key, String value) {
        if (key == null || value == null) return false;
        switch (key) {
            case "sound_effects_enabled": return "0".equals(value) || "1".equals(value);
            case "time_12_24": return "12".equals(value) || "24".equals(value);
            case "screen_off_timeout": return "300000".equals(value) || "600000".equals(value)
                    || "1200000".equals(value) || "3600000".equals(value) || "2147483647".equals(value);
            default: return false;
        }
    }
}
