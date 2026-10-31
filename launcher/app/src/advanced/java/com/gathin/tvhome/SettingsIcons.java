package com.gathin.tvhome;

/** Semantic icons shared by the settings hierarchy, option rows and information cards. */
final class SettingsIcons {
    private SettingsIcons() {}

    static int forLabel(String label, int fallback) {
        switch (label) {
            case "Back to settings":
            case "Back to Other settings":
            case "Back to Network settings":
            case "Back to apps":
            case "Back to Wireless network":
            case "Back to Bluetooth":
                return R.drawable.settings_back;
            case "Bluetooth":
            case "Bluetooth search":
            case "BT Switch":
            case "Search for Bluetooth devices":
            case "Paired devices":
            case "Available devices":
            case "Show paired devices":
            case "Bluetooth audio":
            case "Manage Bluetooth devices":
                return R.drawable.settings_bluetooth;
            case "Language and keyboard":
            case "Language settings":
            case "Current language":
            case "Time zone":
                return R.drawable.settings_language;
            case "Input Method Settings":
            case "Manage keyboards":
                return R.drawable.settings_keyboard;
            case "Date and time":
            case "Date":
                return R.drawable.settings_calendar;
            case "Time":
            case "Auto time":
            case "Use 24-hour format":
            case "Clock format":
                return R.drawable.settings_clock;
            case "Other settings":
            case "Settings":
                return R.drawable.settings_sliders;
            case "System information":
                return R.drawable.settings_info;
            case "Auto Focus":
            case "One-Touch Calibration":
                return R.drawable.settings_focus;
            case "Projection settings":
            case "Auto Trapezoidal Correction":
            case "Manual trapezoidal":
                return R.drawable.settings_keystone;
            case "Digital scaling":
                return R.drawable.settings_scale;
            case "Installation mode":
                return R.drawable.settings_rotate;
            case "Reset trapezoidal correction":
            case "Refresh Network":
            case "Refresh list":
            case "Search again":
                return R.drawable.settings_refresh;
            case "Hotspot settings":
            case "Hotspot switch":
            case "Select AP frequency band":
                return R.drawable.settings_hotspot;
            case "Add network":
                return R.drawable.settings_add;
            case "Network list":
            case "Nearby networks":
                return R.drawable.settings_list;
            case "Display password":
                return R.drawable.settings_eye;
            case "Security":
            case "Device app options":
                return R.drawable.settings_shield;
            case "OK / Cancel":
                return R.drawable.settings_check;
            case "Sound":
            case "Sound controls":
            case "Media volume":
            case "Set media volume":
            case "Toggle mute":
            case "Mute":
            case "Button sound":
            case "Available audio outputs":
            case "Available outputs":
                return R.drawable.settings_volume;
            case "Screen saver":
                return R.drawable.settings_moon;
            case "Timed shutdown":
            case "Stop search":
                return R.drawable.settings_power;
            case "Restore factory settings":
                return R.drawable.settings_warning;
            case "Developer mode":
            case "ADB debug":
            case "Android Developer options":
                return R.drawable.settings_code;
            case "USB debug":
            case "Files & USB":
            case "Storage & USB":
                return R.drawable.settings_usb;
            case "Storage":
            case "Internal storage":
            case "Storage space":
            case "Used":
            case "Available":
            case "Application size":
            case "Caching data":
                return R.drawable.settings_drive;
            case "Equipment model":
            case "UI version":
            case "Android version":
            case "Running memory":
            case "Version":
                return R.drawable.settings_chip;
            case "Serial number":
            case "Wireless MAC":
            case "Package":
            case "Installation":
            case "Network SSID":
                return R.drawable.settings_id;
            case "Local upgrade":
            case "Online upgrade":
                return R.drawable.settings_update;
            case "Appearance":
                return R.drawable.settings_palette;
            case "Uninstall":
                return R.drawable.settings_delete;
            case "Clear cache":
                return R.drawable.settings_clean;
            case "Password":
                return R.drawable.settings_lock;
            case "Network settings":
            case "Wireless network":
            case "Current network":
            case "WI-FI switch":
                return R.drawable.ic_wifi;
            case "Wired network":
            case "IP settings":
            case "Wired status and addresses":
            case "IP addresses / prefix":
            case "Gateway":
            case "DNS servers":
            case "Connection":
                return R.drawable.ic_ethernet;
            default:
                return fallback;
        }
    }
}
