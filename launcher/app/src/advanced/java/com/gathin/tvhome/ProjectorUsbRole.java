package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import java.lang.reflect.Method;

/** Exact stock USB debug control: device/host role, not the global ADB switch. */
final class ProjectorUsbRole {
    private static final String KEY = "persist.sys.usb0device";
    private final Method get, set;
    ProjectorUsbRole() throws Exception {
        if (!"Projector_20260713.153921".equals(android.os.Build.ID) || android.os.Build.VERSION.SDK_INT != 34
                || android.os.Process.myUid() / 100000 != 0)
            throw new IllegalStateException("Unreviewed projector firmware/user");
        Class<?> properties = Class.forName("android.os.SystemProperties");
        get = properties.getMethod("get", String.class, String.class);
        set = properties.getMethod("set", String.class, String.class);
    }
    String read() throws Exception {
        String value = (String)get.invoke(null, KEY, "");
        if (!"0".equals(value) && !"1".equals(value)) throw new IllegalStateException("Unknown USB role; nothing changed");
        return value;
    }
    void set(Context context, String reviewed, boolean device) throws Exception {
        String target = device ? "1" : "0";
        if (!read().equals(reviewed)) throw new IllegalStateException("USB role changed. Reopen to review.");
        if (target.equals(reviewed)) return;
        SharedPreferences undo = context.getSharedPreferences("usb_role_undo", Context.MODE_PRIVATE);
        if (!undo.contains("original") && !undo.edit().putString("original", reviewed).commit())
            throw new IllegalStateException("Could not save original USB role. Nothing changed.");
        if (!read().equals(reviewed)) throw new IllegalStateException("USB role changed. Reopen to review.");
        set.invoke(null, KEY, target);
        if (!target.equals(read())) throw new IllegalStateException("USB role was not confirmed. Reopen to inspect.");
    }
}
