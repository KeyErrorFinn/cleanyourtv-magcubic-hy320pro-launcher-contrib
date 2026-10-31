package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import java.util.Objects;

/** Fixed owner-approved keys only. No generic key writer, pairing or authentication control. */
final class ApprovedSettingsControl {
    enum DebugKey {
        ADB("adb_enabled"), WIRELESS("adb_wifi_enabled");
        final String name;
        DebugKey(String name) { this.name = name; }
    }
    private final Context context;
    private final SharedPreferences journal;
    ApprovedSettingsControl(Context context) {
        this.context = context;
        journal = context.getSharedPreferences("approved_settings_undo", Context.MODE_PRIVATE);
    }
    private void authority() {
        if (context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Settings access is not enabled. Nothing changed.");
    }
    private void capture(String namespace, String key, String value) {
        String id = namespace + "." + key;
        if (journal.getBoolean(id + ".recorded", false)) return;
        if (!journal.edit().putBoolean(id + ".recorded", true)
                .putBoolean(id + ".present", value != null).putString(id, value).commit())
            throw new IllegalStateException("Could not save the original value. Nothing changed.");
    }
    String debug(DebugKey key) { return Settings.Global.getString(context.getContentResolver(), key.name); }
    void debug(DebugKey key, String reviewed, boolean enabled) {
        authority();
        if (!Objects.equals(reviewed, debug(key))) throw new IllegalStateException("Setting changed. Reopen and try again.");
        String value = enabled ? "1" : "0";
        if (Objects.equals(reviewed, value)) return;
        capture("global", key.name, reviewed);
        if (!Settings.Global.putString(context.getContentResolver(), key.name, value)
                || !Objects.equals(value, debug(key)))
            throw new IllegalStateException("Change was not confirmed. Reopen to check its state.");
    }
    String enabledKeyboards(){return Settings.Secure.getString(context.getContentResolver(),"enabled_input_methods");}
    void enableKeyboard(String reviewed,String selected,String id,boolean enabled){
        authority();InputMethodManager manager=context.getSystemService(InputMethodManager.class);boolean installed=false;
        if(manager!=null)for(InputMethodInfo method:manager.getInputMethodList())if(method.getId().equals(id))installed=true;
        if(!installed)throw new IllegalStateException("This keyboard is no longer installed.");
        KeyboardEnablementChange.apply(new KeyboardEnablementChange.Store(){
            public String read(){return enabledKeyboards();}
            public String selected(){return keyboard();}
            public boolean write(String value){return Settings.Secure.putString(context.getContentResolver(),"enabled_input_methods",value);}
            public void capture(String original){ApprovedSettingsControl.this.capture("secure","enabled_input_methods",original);}
        },reviewed,selected,id,enabled);
    }
    String keyboard() { return Settings.Secure.getString(context.getContentResolver(), "default_input_method"); }
    String subtype() { return Settings.Secure.getString(context.getContentResolver(), "selected_input_method_subtype"); }
    void keyboard(String reviewed, String reviewedSubtype, String id) {
        authority();
        InputMethodManager input = context.getSystemService(InputMethodManager.class);
        boolean enabled = false;
        if (input != null) for (InputMethodInfo item : input.getEnabledInputMethodList())
            if (item.getId().equals(id)) enabled = true;
        if (!enabled) throw new IllegalStateException("This keyboard is no longer enabled. Nothing changed.");
        if (!Objects.equals(reviewed, keyboard()) || !Objects.equals(reviewedSubtype, subtype()))
            throw new IllegalStateException("Keyboard changed. Reopen and try again.");
        if (id.equals(reviewed)) return;
        KeyboardSelectionChange.apply(new KeyboardSelectionChange.Backend() {
            public String id() { return keyboard(); }
            public String subtype() { return ApprovedSettingsControl.this.subtype(); }
            public boolean id(String value) { return Settings.Secure.putString(context.getContentResolver(), "default_input_method", value); }
            public boolean subtype(String value) { return Settings.Secure.putString(context.getContentResolver(), "selected_input_method_subtype", value); }
            public void capture(String original, String subtype) {
                ApprovedSettingsControl.this.capture("secure", "default_input_method", original);
                ApprovedSettingsControl.this.capture("secure", "selected_input_method_subtype", subtype);
            }
        }, reviewed, reviewedSubtype, id);
    }
}
