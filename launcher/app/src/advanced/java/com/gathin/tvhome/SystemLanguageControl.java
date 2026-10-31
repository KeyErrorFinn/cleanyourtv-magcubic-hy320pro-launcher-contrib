package com.gathin.tvhome;

import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.LocaleList;
import java.lang.reflect.InvocationTargetException;

/** Locale list only. No raw configuration, setting-key or Binder-transaction input. */
final class SystemLanguageControl {
    private final Context context;
    private final LocaleManager manager;
    private final SharedPreferences undo;
    SystemLanguageControl(Context context) {
        this.context = context;
        manager = android.os.Build.VERSION.SDK_INT >= 33 ? context.getSystemService(LocaleManager.class) : null;
        undo = context.getSharedPreferences("system_language_undo", Context.MODE_PRIVATE);
    }
    String read() {
        if (android.os.Build.VERSION.SDK_INT < 33 || manager == null) throw new IllegalStateException("System language service unavailable");
        return manager.getSystemLocales().toLanguageTags();
    }
    String original() { return undo.getString("original", null); }
    void set(String reviewed, String tags) throws Exception {
        if (android.os.Build.VERSION.SDK_INT < 33) throw new IllegalStateException("System language service unavailable");
        if (context.checkSelfPermission("android.permission.CHANGE_CONFIGURATION") != PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("System-language access is not enabled");
        LocaleList locales = LocaleList.forLanguageTags(tags);
        if (locales.isEmpty() || !tags.equals(locales.toLanguageTags())) throw new IllegalArgumentException("Invalid language list");
        // The stock language page uses LocalePicker. No access exemptions or raw Binder fallback.
        java.lang.reflect.Method setter = Class.forName("com.android.internal.app.LocalePicker").getMethod("updateLocales", LocaleList.class);
        if (!read().equals(reviewed)) throw new IllegalStateException("Language changed elsewhere. Reopen to review.");
        if (reviewed.equals(tags)) return;
        if (!undo.contains("original") && !undo.edit().putString("original", reviewed).commit())
            throw new IllegalStateException("Could not save the original languages. Nothing changed.");
        try { setter.invoke(null, locales); }
        catch (InvocationTargetException failure) {
            throw new IllegalStateException("Firmware refused the language change", failure.getCause());
        }
        for (int n=0; n<10 && !tags.equals(read()); n++) android.os.SystemClock.sleep(100);
        if (!tags.equals(read())) throw new IllegalStateException("Language change was not confirmed. Reopen to inspect.");
    }
    boolean available() {
        if (android.os.Build.VERSION.SDK_INT < 33) return false;
        try {
            Class.forName("com.android.internal.app.LocalePicker").getMethod("updateLocales", LocaleList.class);
            return context.checkSelfPermission("android.permission.CHANGE_CONFIGURATION") == PackageManager.PERMISSION_GRANTED;
        } catch (ReflectiveOperationException | SecurityException failure) { return false; }
    }
}
