package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    static final String EXTRA_REORDER = "com.gathin.tvhome.extra.REORDER_FROM_SETTINGS";
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private static final int[] DOCK_OPACITIES = {100, 70, 50, 30};
    private final TextView[] dockChoices = new TextView[DOCK_OPACITIES.length];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView openOnStartChoice;
    private int columns;
    private int rowsBelowDock;
    private int dockOpacity;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!"Projector_20260713.153921".equals(android.os.Build.ID)
                || android.os.Build.VERSION.SDK_INT != 34
                || !"rk3566_rgo".equals(android.os.Build.DEVICE)
                || !"exdroid".equals(android.os.Build.BOARD)) {
            Toast.makeText(this, "Integrated settings require the verified HY320 Pro firmware.", Toast.LENGTH_LONG).show();
            try { startActivity(SystemSettings.intent(this, Settings.ACTION_SETTINGS)); }
            catch (RuntimeException unavailable) { /* Home remains accessible. */ }
            finish(); return;
        }

        if ("network".equals(getIntent().getStringExtra("settings_page"))) showNetwork();
        else showHub();
    }

    private String page = "hub";
    private final java.util.Map<String, String> pageFocus = new java.util.HashMap<>();
    private final java.util.concurrent.ExecutorService pageLoader = java.util.concurrent.Executors.newSingleThreadExecutor();
    private int pageGeneration;
    private String selectedApp;
    private boolean returningFromDeviceControls;
    private static final int PAIRED_DEVICES_PERMISSION = 42;
    private static final int SCAN_PERMISSION = 43;
    private android.bluetooth.BluetoothAdapter scanningAdapter;
    private android.content.BroadcastReceiver discoveryReceiver;
    private boolean discoveryRegistered;
    private boolean ownsDiscovery;
    private final android.os.Handler scanHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.Set<String> discoveredAddresses = new java.util.HashSet<>();
    private LinearLayout scanResults;
    private TextView scanStatus;
    private WifiScanSession wifiScan;
    private TextView soundVolume;
    private TextView soundMute;
    private OrientationPilotUi orientationPilot;
    private ManualCornerUi manualCornerPreview;
    private ProjectionResetUi projectionReset;
    private ProjectionScaleUi projectionScale;
    private AutomaticProjectionUi automaticProjection;
    private BluetoothAudioUi bluetoothAudio;
    private final android.os.Handler soundHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private void showAppearance() {
        page = "appearance";
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        View reorderChoice = findViewById(R.id.reorderChoice);
        reorderChoice.setVisibility(config.launcherSettingsButton ? View.VISIBLE : View.GONE);
        reorderChoice.setOnClickListener(v -> {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_REORDER, true));
            finish();
        });
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        dockOpacity = config.dockOpacity;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                changed();
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                changed();
                refresh();
            });
        }
        int[] dockIds = {R.id.dock100, R.id.dock70, R.id.dock50, R.id.dock30};
        for (int i = 0; i < dockIds.length; i++) {
            final int value = DOCK_OPACITIES[i];
            dockChoices[i] = findViewById(dockIds[i]);
            dockChoices[i].setOnClickListener(v -> {
                if (dockOpacity == value) return;
                dockOpacity = value;
                choices.edit().putInt(Config.DOCK_OPACITY_KEY, value).apply();
                changed();
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
            reverseRow(dockChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            changed();
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new SettingsPopup.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        openOnStartChoice = findViewById(R.id.openOnStartChoice);
        if (config.openOnStartChoice) {
            openOnStartChoice.setVisibility(View.VISIBLE);
            openOnStartChoice.setOnClickListener(v -> showOpenOnStartPicker());
        }
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove(Config.DOCK_OPACITY_KEY).remove(OpenOnStart.KEY).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            dockOpacity = current.dockOpacity;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            changed();
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v -> showHub());
        refresh();
        if (config.launcherSettingsButton) reorderChoice.requestFocus();
        else columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private LinearLayout pageRows(String title, String subtitle) {
        if(bluetoothAudio!=null){bluetoothAudio.close();bluetoothAudio=null;}
        stopBluetoothScan();
        stopWifiScan();
        scanStatus = null;
        scanResults = null;
        pageGeneration++;
        soundVolume = null;
        soundMute = null;
        setContentView(R.layout.activity_settings_hub);
        ((TextView) findViewById(R.id.settingsHeading)).setText(title);
        ((android.widget.ImageView) findViewById(R.id.settingsPageIcon)).setImageResource(SettingsIcons.forLabel(title, R.drawable.ic_apps));
        ((TextView) findViewById(R.id.settingsSubtitle)).setText(subtitle);
        return findViewById(R.id.settingsHubRows);
    }

    /** Informational values wrap instead of disappearing into the small trailing status column. */
    private TextView infoRow(LinearLayout rows, String label, String value) {
        View row = getLayoutInflater().inflate(R.layout.item_settings_info, rows, false);
        ((TextView) row.findViewById(R.id.infoLabel)).setText(label);
        ((android.widget.ImageView) row.findViewById(R.id.infoIcon)).setImageResource(SettingsIcons.forLabel(label, R.drawable.settings_info));
        ((TextView) row.findViewById(R.id.infoValue)).setText(value);
        rows.addView(row);
        return row.findViewById(R.id.infoValue);
    }

    private void backRow(LinearLayout rows) {
        if ("sound".equals(page) || "storage".equals(page))
            hubRow(rows, R.drawable.ic_settings, "Back to Other settings", "", this::showOther);
        else hubRow(rows, R.drawable.ic_settings, "Back to settings", "", this::showHub);
    }

    private void systemRow(LinearLayout rows, String title, String action) {
        Intent intent = new Intent(action);
        if (getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null)
            hubRow(rows, R.drawable.ic_settings, title, "Open device controls", () -> open(intent));
    }

    private void hubRow(LinearLayout rows, int icon, String title, String hint, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        row.setBackgroundResource(R.drawable.settings_preview_row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        lp.bottomMargin = dp(8);
        row.setLayoutParams(lp);
        ((android.widget.ImageView) row.findViewById(R.id.icon)).setImageResource(SettingsIcons.forLabel(title, icon));
        TextView titleView = row.findViewById(R.id.title);
        titleView.setText(title);
        titleView.setSingleLine(false);
        titleView.setMaxLines(3);
        TextView status = row.findViewById(R.id.status);
        status.setText(hint);
        status.setTypeface(getResources().getFont(R.font.outfit_regular));
        status.setTextSize(14);
        status.setTextColor(new android.content.res.ColorStateList(
                new int[][] {new int[] {android.R.attr.state_focused}, new int[] {}},
                new int[] {0xFF333333, 0xFFE6E6EB}));
        status.setMaxWidth(dp(155));
        status.setSingleLine(true);
        status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        if (action != null) row.setOnClickListener(v -> action.run());
        else { row.setFocusable(false); row.setClickable(false); }
        row.setTag(title);
        final String ownerPage = page;
        row.setOnFocusChangeListener((v, focused) -> {
            if (focused) pageFocus.put(ownerPage, title);
        });
        rows.addView(row);
    }

    private Intent nativeSettings() {
        return SystemSettings.intent(this, Settings.ACTION_SETTINGS);
    }

    private void showHub() {
        page = "hub";
        LinearLayout rows = pageRows("Settings", "Projector settings.");
        hubRow(rows, R.drawable.ic_picture, "Projection settings", "Focus, keystone & scaling", this::showProjector);
        hubRow(rows, R.drawable.ic_wifi, "Network settings", networkName(), this::showNetwork);
        hubRow(rows, R.drawable.ic_settings, "Bluetooth", "Audio & accessories", this::showBluetooth);
        hubRow(rows, R.drawable.ic_apps, "Application Manager", "Installed applications", this::showApps);
        hubRow(rows, R.drawable.ic_settings, "Language and keyboard", "Language & input", this::showLanguage);
        hubRow(rows, R.drawable.ic_settings, "Date and time", "Clock & time zone", this::showDateTime);
        hubRow(rows, R.drawable.ic_settings, "Other settings", "Appearance, sound & more", this::showOther);
        hubRow(rows, R.drawable.ic_apps, "System information", "Device, storage & updates", this::showAbout);
        focusFirst(rows);
    }

    private void focusFirst(LinearLayout rows) {
        String previous = pageFocus.get(page);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (previous != null && previous.equals(row.getTag()) && row.isFocusable()) {
                row.requestFocus();
                return;
            }
        }
        for (int i = 0; i < rows.getChildCount(); i++) {
            if (rows.getChildAt(i).isFocusable()) { rows.getChildAt(i).requestFocus(); return; }
        }
    }

    /** No private activities, vendor commands, injected keys or elevated settings writes. */
    private void deviceOption(LinearLayout rows, String label, String detail, String path, Intent direct) {
        deviceValueOption(rows, label, "Device controls", detail, path, direct);
    }

    private void deviceValueOption(LinearLayout rows, String label, String value, String detail, String path, Intent direct) {
        hubRow(rows, SettingsIcons.forLabel(label,"wifi-networks".equals(page) ? R.drawable.ic_wifi : R.drawable.ic_settings), label, value, () -> {
            if(direct==null && projectionRecoveryPending()) {Toast.makeText(this,"An adjustment is still finishing. Try again shortly.",Toast.LENGTH_LONG).show();return;}
            if (!java.util.Arrays.asList("Restore factory settings","One-Touch Calibration","Reset trapezoidal correction","Local upgrade","Online upgrade","Clear cache").contains(label)) {
                open(direct == null ? nativeSettings() : direct); return;
            }
            AlertDialog dialog = new SettingsPopup.Builder(this).setTitle(label)
                    .setMessage(detail + "\n\nOn the next screen: " + path + ".")
                    .setPositiveButton("Open device controls", (d, which) -> open(direct == null ? nativeSettings() : direct))
                    .setNegativeButton(android.R.string.cancel, null).create();
            dialog.setOnShowListener(d -> {
                SettingsPopup.style(dialog);
                TextView message = dialog.findViewById(android.R.id.message);
                if (message != null) {
                    message.setTypeface(getResources().getFont(R.font.outfit_regular));
                    message.setTextSize(18);
                    message.setTextColor(0xFFF5F5FA);
                }
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();
            });
            dialog.show();
        });
    }

    private void stockOption(LinearLayout rows, String label, String detail, String section) {
        deviceOption(rows, label, detail, section + " > " + label, null);
    }

    /** Use a supported Android entry point directly instead of the stock root and a route popup. */
    private void directSystemOption(LinearLayout rows, String label, String action) {
        Intent intent = new Intent(action);
        if (getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null)
            hubRow(rows, SettingsIcons.forLabel(label,R.drawable.ic_settings), label, "System screen", () -> open(intent));
    }

    private Intent networkControls() {
        return new Intent().setClassName("com.htc.magcubicos", "com.htc.magcubicos.activity.settings.NetworkActivity");
    }

    private void showLanguage() {
        page = "language";
        LinearLayout rows = pageRows("Language and keyboard", "Language and input.");
        infoRow(rows, "Current language", java.util.Locale.getDefault().getDisplayName());
        SystemLanguageControl language = new SystemLanguageControl(this);
        hubRow(rows, R.drawable.settings_language, "System language", language.available() ? "Choose language" : "Firmware access unavailable", this::chooseSystemLanguage);
        hubRow(rows, R.drawable.settings_keyboard, "Choose keyboard", "Already-enabled keyboards", this::chooseEnabledKeyboard);
        hubRow(rows, R.drawable.settings_keyboard, "Manage keyboards", "Installed keyboards", this::manageKeyboards);
        backRow(rows);
        focusFirst(rows);
    }

    private void showOther() {
        page = "other";
        LinearLayout rows = pageRows("Other settings", "Preferences and customisation.");
        standardPreferenceRow(rows, "Button sound", "sound_effects_enabled", new String[] {"0", "1"}, new String[] {"Off", "On"}, this::showOther);
        standardPreferenceRow(rows, "Screen saver", "screen_off_timeout", new String[] {"300000", "600000", "1200000", "3600000", "2147483647"}, new String[] {"5 minutes", "10 minutes", "20 minutes", "1 hour", "Off"}, this::showOther);
        hubRow(rows,R.drawable.settings_power,"Timed shutdown","Off, 5, 10, 20 minutes or 1 hour",()->SessionSettingsUi.timer(this));
        stockOption(rows, "Restore factory settings", "Warning: a factory reset erases apps, accounts and settings, including CleanYourTV. Opening device controls does not perform a reset. Do not continue unless you intend to erase the projector.", "Other settings");
        hubRow(rows, R.drawable.ic_settings, "Developer mode", "Debugging controls", this::showDeveloper);
        hubRow(rows,R.drawable.settings_code,"ADB settings helper",SettingsSessionClient.connected()?"Connected for this session":"Not connected",()->SessionSettingsUi.helper(this));
        if(SessionSettingsUi.starterAvailable(this))hubRow(rows,R.drawable.settings_code,"Settings Starter","Automatic startup & connection status",()->SessionSettingsUi.openStarter(this));
        hubRow(rows, R.drawable.ic_home_settings, "Appearance", "Dock, wallpaper & motion", this::showAppearance);
        hubRow(rows, R.drawable.ic_picture, "Sound", "Volume & audio output", this::showSound);
        hubRow(rows, R.drawable.ic_apps, "Storage & USB", "Space & files", this::showStorage);
        backRow(rows);
        focusFirst(rows);
    }

    private void standardPreferenceRow(LinearLayout rows, String label, String key, String[] values, String[] labels, Runnable refreshPage) {
        String current = Settings.System.getString(getContentResolver(), key);
        int checked = -1;
        for (int i = 0; i < values.length; i++) if (values[i].equals(current)) checked = i;
        String status = checked < 0 ? "System default" : labels[checked];
        final int[] selected = {checked};
        hubRow(rows, SettingsIcons.forLabel(label, R.drawable.settings_sliders), label, status, () -> {
            if (!Settings.System.canWrite(this)) {
                Toast.makeText(this, "System-settings access is not enabled. No value changed.", Toast.LENGTH_LONG).show();
                return;
            }
            AlertDialog dialog = new SettingsPopup.Builder(this).setTitle(label)
                    .setSingleChoiceItems(labels, selected[0], (d, which) -> selected[0] = which)
                    .setPositiveButton("Apply", (d, which) -> {
                        if (selected[0] < 0) return;
                        String value = values[selected[0]];
                        if (!StandardPreferenceValues.allowed(key, value)) return;
                        String original = Settings.System.getString(getContentResolver(), key);
                        SharedPreferences undo = getSharedPreferences("standard_settings_undo", MODE_PRIVATE);
                        if (!undo.contains(key + ".recorded")) undo.edit().putBoolean(key + ".recorded", true)
                                .putBoolean(key + ".present", original != null).putString(key, original).commit();
                        try {
                            boolean saved = Settings.System.putString(getContentResolver(), key, value);
                            if (!saved || !value.equals(Settings.System.getString(getContentResolver(), key)))
                                Toast.makeText(this, "The projector did not accept this change.", Toast.LENGTH_LONG).show();
                        } catch (SecurityException e) {
                            Toast.makeText(this, "System-settings access was refused. Use the system page.", Toast.LENGTH_LONG).show();
                        }
                        refreshPage.run();
                    }).setNegativeButton(android.R.string.cancel, null).create();
            styleDialog(dialog);
        });
    }

    private void styleDialog(AlertDialog dialog) {
        dialog.setOnShowListener(d -> {
            SettingsPopup.style(dialog);
            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) {
                message.setTypeface(getResources().getFont(R.font.outfit_regular));
                message.setTextSize(18);
                message.setTextColor(0xFFF5F5FA);
            }
        });
        dialog.show();
    }

    private void showDeveloper() {
        page = "developer";
        LinearLayout rows = pageRows("Developer mode", "Debugging, when you need it.\n\nAndroid Developer options includes Wireless debugging. The stock USB and ADB switches are separate.");
        Intent development = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        if (getPackageManager().resolveActivity(development, PackageManager.MATCH_DEFAULT_ONLY) != null)
            hubRow(rows, R.drawable.settings_code, "Android Developer options", "Wireless debugging", () -> open(development));
        hubRow(rows,R.drawable.settings_list,"Paired computers","Manage debugging authorisations",()->SessionDebugUi.devices(this,this::showDeveloper));
        hubRow(rows,R.drawable.settings_code,"Pair computer","Two-minute pairing window",()->SessionDebugUi.pair(this));
        usbRoleRow(rows);
        debuggingRow(rows, "ADB debug", ApprovedSettingsControl.DebugKey.ADB);
        debuggingRow(rows, "Wireless debugging", ApprovedSettingsControl.DebugKey.WIRELESS);
        hubRow(rows, R.drawable.ic_settings, "Back to Other settings", "", this::showOther);
        focusFirst(rows);
    }

    private void debuggingRow(LinearLayout rows, String label, ApprovedSettingsControl.DebugKey key) {
        ApprovedSettingsControl control = new ApprovedSettingsControl(this);
        String reviewed = control.debug(key);
        hubRow(rows, R.drawable.settings_code, label, "1".equals(reviewed) ? "On" : "Off", () -> {
            final int[] choice = {"1".equals(reviewed) ? 1 : 0};
            styleDialog(new SettingsPopup.Builder(this).setTitle(label)
                    .setSingleChoiceItems(new String[]{"Off", "On"}, choice[0], (d, which) -> choice[0] = which)
                    .setPositiveButton("Apply", (d, which) -> {
                        Runnable apply = () -> {
                            try { control.debug(key, reviewed, choice[0] == 1); }
                            catch (RuntimeException failure) { Toast.makeText(this, failure.getMessage(), Toast.LENGTH_LONG).show(); }
                            showDeveloper();
                        };
                        if (choice[0] == 0 && "1".equals(reviewed))
                            styleDialog(new SettingsPopup.Builder(this).setTitle("Turn off " + label + "?")
                                .setMessage("This can disconnect the computer. You can turn it on again here with the remote. Pairings and USB port role are unchanged.")
                                .setPositiveButton("Turn off", (confirm, button) -> apply.run())
                                .setNegativeButton(android.R.string.cancel, null).create());
                        else apply.run();
                    }).setNegativeButton(android.R.string.cancel, null).create());
        });
    }

    private void usbRoleRow(LinearLayout rows) {
        try {
            ProjectorUsbRole role = new ProjectorUsbRole();
            String reviewed = role.read();
            hubRow(rows, R.drawable.settings_usb, "USB debug", "1".equals(reviewed) ? "On Â· device mode" : "Off Â· USB host", () -> {
                final int[] choice = {"1".equals(reviewed) ? 1 : 0};
                styleDialog(new SettingsPopup.Builder(this).setTitle("USB debug")
                    .setSingleChoiceItems(new String[]{"Off â€” USB host", "On â€” USB device mode"}, choice[0], (d,w) -> choice[0]=w)
                    .setPositiveButton("Apply", (d,w) -> {
                        if ((choice[0] == 1) == "1".equals(reviewed)) return;
                        styleDialog(new SettingsPopup.Builder(this).setTitle("Change USB port role?")
                            .setMessage("This is the manufacturer's USB debug control. Changing it can disconnect USB drives, receivers, keyboards or USB Ethernet. It does not pair ADB or change Wireless debugging.")
                            .setPositiveButton("Change role", (confirm,which) -> pageLoader.execute(() -> {
                                String error=null;
                                try { role.set(this,reviewed,choice[0]==1); } catch(Exception failure){error="USB role change was not confirmed. Reopen to check its state.";}
                                final String message=error;
                                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;if(message!=null)Toast.makeText(this,message,Toast.LENGTH_LONG).show();if("developer".equals(page))showDeveloper();});
                            })).setNegativeButton(android.R.string.cancel,null).create());
                    }).setNegativeButton(android.R.string.cancel,null).create());
            });
        } catch(Exception failure) { infoRow(rows,"USB debug","Firmware access unavailable. USB role unchanged."); }
    }

    private void chooseSystemLanguage() {
        SystemLanguageControl control = new SystemLanguageControl(this);
        if (!control.available()) {
            styleDialog(new SettingsPopup.Builder(this).setTitle("System language")
                .setMessage("This firmware does not expose the required locale setter to CleanYourTV. The permission alone cannot make that API available. No language changed.")
                .setPositiveButton(android.R.string.ok,null).create()); return;
        }
        String reviewed=control.read();
        java.util.TreeMap<String,String> names=new java.util.TreeMap<>();
        for(String tag:android.content.res.Resources.getSystem().getAssets().getLocales()) {
            java.util.Locale locale=java.util.Locale.forLanguageTag(tag.replace('_','-'));
            if(locale.getLanguage().isEmpty() || "zz".equals(locale.getLanguage()) || "en-XA".equals(locale.toLanguageTag()) || "en-XC".equals(locale.toLanguageTag()) || "ar-XB".equals(locale.toLanguageTag()))continue;
            String display=locale.getDisplayName(java.util.Locale.getDefault()),nativeName=locale.getDisplayName(locale);
            names.put(display.equals(nativeName)?display:display+" Â· "+nativeName,locale.toLanguageTag());
        }
        String[] labels=names.keySet().toArray(new String[0]),tags=names.values().toArray(new String[0]);
        int selected=-1;String first=android.os.LocaleList.forLanguageTags(reviewed).get(0).toLanguageTag();
        for(int i=0;i<tags.length;i++)if(tags[i].equals(first))selected=i;
        final int[] choice={selected};
        AlertDialog.Builder builder=new SettingsPopup.Builder(this).setTitle("System language")
            .setSingleChoiceItems(labels,selected,(d,w)->choice[0]=w)
            .setPositiveButton("Apply",(d,w)->{if(choice[0]>=0)saveSystemLanguage(control,reviewed,LanguageListValues.promote(reviewed,tags[choice[0]]));})
            .setNegativeButton(android.R.string.cancel,null);
        if(control.original()!=null)builder.setNeutralButton("Restore original",(d,w)->saveSystemLanguage(control,reviewed,control.original()));
        styleDialog(builder.create());
    }

    private void saveSystemLanguage(SystemLanguageControl control,String reviewed,String tags) {
        pageLoader.execute(()->{
            String error=null;try{control.set(reviewed,tags);}catch(Exception failure){error=failure.getMessage();}
            final String message=error;runOnUiThread(()->{if(isFinishing()||isDestroyed())return;if(message!=null)Toast.makeText(this,message,Toast.LENGTH_LONG).show();if("language".equals(page))showLanguage();});
        });
    }

    private void manageKeyboards() {
        page="keyboards";
        LinearLayout rows=pageRows("Manage keyboards","Your installed input methods.\n\nChoose among enabled keyboards here. Select a keyboard to enable or disable it.");
        android.view.inputmethod.InputMethodManager input=getSystemService(android.view.inputmethod.InputMethodManager.class);
        if(input!=null) {
            java.util.Set<String> enabled=new java.util.HashSet<>();
            for(android.view.inputmethod.InputMethodInfo method:input.getEnabledInputMethodList())enabled.add(method.getId());
            String current=new ApprovedSettingsControl(this).keyboard();
            for(android.view.inputmethod.InputMethodInfo method:input.getInputMethodList()) {
                String id=method.getId();
                String label=method.loadLabel(getPackageManager()).toString();boolean active=enabled.contains(id);
                hubRow(rows,R.drawable.settings_keyboard,label,id.equals(current)?"Current (enabled)":active?"Enabled":"Disabled",()->{
                    if(id.equals(current)){Toast.makeText(this,"Choose another keyboard before turning this one off.",Toast.LENGTH_LONG).show();return;}
                    ApprovedSettingsControl control=new ApprovedSettingsControl(this);String original=control.enabledKeyboards(),selected=control.keyboard();
                    styleDialog(new SettingsPopup.Builder(this).setTitle((active?"Disable ":"Enable ")+label+"?")
                        .setMessage(active?"This keyboard will no longer be available for input.":"This keyboard can read the text you type, including passwords. Enable it only if you trust it.")
                        .setPositiveButton(active?"Disable":"Enable",(dialog,which)->{
                            try{control.enableKeyboard(original,selected,id,!active);}catch(RuntimeException failure){Toast.makeText(this,failure.getMessage(),Toast.LENGTH_LONG).show();}
                            manageKeyboards();
                        }).setNegativeButton("Cancel",null).create());
                });
            }
        }
        hubRow(rows,R.drawable.settings_keyboard,"Choose keyboard","Enabled keyboards",this::chooseEnabledKeyboard);
        hubRow(rows,R.drawable.settings_back,"Back to Language and keyboard","",this::showLanguage);focusFirst(rows);
    }

    private void chooseEnabledKeyboard() {
        android.view.inputmethod.InputMethodManager input = getSystemService(android.view.inputmethod.InputMethodManager.class);
        if (input == null) return;
        java.util.List<android.view.inputmethod.InputMethodInfo> methods = input.getEnabledInputMethodList();
        if (methods.isEmpty()) { Toast.makeText(this, "No enabled keyboard was found.", Toast.LENGTH_LONG).show(); return; }
        ApprovedSettingsControl control = new ApprovedSettingsControl(this);
        String reviewed = control.keyboard(), subtype = control.subtype();
        String[] labels = new String[methods.size()];
        int checked = -1;
        for (int i = 0; i < methods.size(); i++) {
            labels[i] = methods.get(i).loadLabel(getPackageManager()).toString();
            if (methods.get(i).getId().equals(reviewed)) checked = i;
        }
        final int[] choice = {checked};
        styleDialog(new SettingsPopup.Builder(this).setTitle("Choose keyboard")
                .setSingleChoiceItems(labels, checked, (d, which) -> choice[0] = which)
                .setPositiveButton("Apply", (d, which) -> {
                    if (choice[0] < 0) return;
                    String id = methods.get(choice[0]).getId();
                    pageLoader.execute(() -> {
                        String error = null;
                        try { control.keyboard(reviewed, subtype, id); }
                        catch (RuntimeException failure) { error = failure.getMessage(); }
                        final String message = error;
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                            if ("language".equals(page)) showLanguage();
                            else if ("keyboards".equals(page)) manageKeyboards();
                        });
                    });
                }).setNegativeButton(android.R.string.cancel, null).create());
    }

    private String networkName() {
        try {
            android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            String ssid = wifi != null ? wifi.getConnectionInfo().getSSID() : null;
            if (ssid != null && !ssid.contains("unknown") && !ssid.equals("0x")) return ssid.replace("\"", "");
        } catch (SecurityException ignored) { }
        return "Network status";
    }

    private void showNetwork() {
        page = "network";
        LinearLayout rows = pageRows("Network settings", "Wi-Fi, hotspot and Ethernet.");
        hubRow(rows, R.drawable.ic_wifi, "Wireless network", networkName(), this::showWifi);
        hubRow(rows, R.drawable.ic_wifi, "Hotspot settings", "Share a connection", this::showHotspot);
        hubRow(rows, R.drawable.ic_settings, "Wired network", "Ethernet status", this::showWired);
        backRow(rows);
        focusFirst(rows);
    }

    private void networkBack(LinearLayout rows) {
        hubRow(rows, R.drawable.ic_settings, "Back to Network settings", "", this::showNetwork);
        focusFirst(rows);
    }

    private void showWifi() {
        page = "wifi";
        LinearLayout rows = pageRows("Wireless network", "Wireless connection.");
        infoRow(rows, "Current network", networkName());
        Intent wifi = SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS);
        hubRow(rows,R.drawable.ic_wifi,"WI-FI switch","On / Off",()->SessionSettingsUi.power(this,false,this::showWifi));
        hubRow(rows,R.drawable.ic_wifi,"Add network","Security & credentials",()->SessionSettingsUi.addNetwork(this,null,this::showWifi));
        hubRow(rows,R.drawable.settings_list,"Saved networks","Connect or forget",()->SessionSettingsUi.network(this,null,this::showWifi));
        hubRow(rows, R.drawable.settings_refresh, "Refresh Network", "Scan nearby networks", () -> showWifiNetworks(true));
        hubRow(rows, R.drawable.settings_list, "Network list", "Nearby networks", this::showWifiNetworks);
        connectionDetails(rows, android.net.NetworkCapabilities.TRANSPORT_WIFI);
        networkBack(rows);
    }

    private void showWifiNetworks() {
        showWifiNetworks(false);
    }

    private void showWifiNetworks(boolean requestScan) {
        page = "wifi-networks";
        LinearLayout rows = pageRows("Nearby networks", "Nearby Wi-Fi networks.");
        hubRow(rows, R.drawable.settings_refresh, "Refresh list", "Scan nearby networks", () -> showWifiNetworks(true));
        hubRow(rows, R.drawable.settings_back, "Back to Wireless network", "", this::showWifi);
        TextView status = infoRow(rows, "Scan status", requestScan ? "Scanning nearby networksÃ¢â‚¬Â¦" : "Showing Android's last scan. Choose Refresh for new results.");
        focusFirst(rows);
        int generation = pageGeneration;
        if (requestScan) {
            wifiScan = new WifiScanSession(this);
            wifiScan.start(message -> {
                if (isFinishing() || isDestroyed() || generation != pageGeneration || !"wifi-networks".equals(page)) return;
                status.setText(message);
                loadWifiNetworks(rows, generation);
            });
        } else loadWifiNetworks(rows, generation);
    }

    private void stopWifiScan() {
        if (wifiScan != null) { wifiScan.close(); wifiScan = null; }
    }

    private void loadWifiNetworks(LinearLayout rows, int generation) {
        pageLoader.execute(() -> {
            List<String[]> entries = new ArrayList<>();
            String error = null;
            try {
                android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
                java.util.Set<String> seen = new java.util.HashSet<>();
                if (wifi != null) for (android.net.wifi.ScanResult scan : wifi.getScanResults()) {
                    String name = scan.SSID;
                    if (name == null || name.isEmpty()) continue;
                    String security = scan.capabilities.contains("SAE") ? "WPA3" : scan.capabilities.contains("PSK") ? "WPA/WPA2" : scan.capabilities.contains("EAP") ? "Enterprise" : scan.capabilities.contains("WEP") ? "WEP" : "Open / other";
                    if (seen.add(name + "|" + security)) entries.add(new String[] {name, security + " / " + android.net.wifi.WifiManager.calculateSignalLevel(scan.level, 5) + " of 4 signal"});
                }
                entries.sort((x, y) -> x[0].compareToIgnoreCase(y[0]));
            } catch (SecurityException e) { error = "Android requires the existing location permission and enabled location services to supply scan results. No setting was changed."; }
            final String message = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != pageGeneration) return;
                if (message != null) infoRow(rows, "Network list", message);
                else if (entries.isEmpty()) infoRow(rows, "Network list", "No visible networks supplied by Android. Refresh requests another scan; Android may limit how often it runs.");
                for (String[] entry : entries) hubRow(rows,R.drawable.ic_wifi,entry[0],entry[1],()->SessionSettingsUi.network(this,entry[0],this::showWifi));
            });
        });
    }

    private void connectionDetails(LinearLayout rows, int transport) {
        android.net.ConnectivityManager manager = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager == null) return;
        for (android.net.Network network : manager.getAllNetworks()) {
            android.net.NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            if (caps == null || !caps.hasTransport(transport)) continue;
            android.net.LinkProperties link = manager.getLinkProperties(network);
            if (link == null) continue;
            List<String> addresses = new ArrayList<>(), dns = new ArrayList<>(), gateways = new ArrayList<>();
            for (android.net.LinkAddress address : link.getLinkAddresses()) addresses.add(address.getAddress().getHostAddress() + "/" + address.getPrefixLength());
            for (java.net.InetAddress address : link.getDnsServers()) dns.add(address.getHostAddress());
            for (android.net.RouteInfo route : link.getRoutes()) if (route.isDefaultRoute() && route.getGateway() != null) gateways.add(route.getGateway().getHostAddress());
            infoRow(rows, "IP addresses / prefix", addresses.isEmpty() ? "Not supplied" : android.text.TextUtils.join("\n", addresses));
            infoRow(rows, "Gateway", gateways.isEmpty() ? "Not supplied" : android.text.TextUtils.join("\n", gateways));
            infoRow(rows, "DNS servers", dns.isEmpty() ? "Not supplied" : android.text.TextUtils.join("\n", dns));
            return;
        }
    }

    private void showHotspot() {
        page = "hotspot";
        LinearLayout rows = pageRows("Hotspot settings", "Share a connection.");
        hubRow(rows,R.drawable.settings_hotspot,"Hotspot switch","On / Off",()->SessionNetworkUi.power(this,this::showHotspot));
        for(String label:new String[]{"Network SSID","Password","Display password","Security","Select AP frequency band","OK / Cancel"})
            hubRow(rows,R.drawable.ic_wifi,label,"Configure hotspot",()->SessionNetworkUi.config(this,label,this::showHotspot));
        networkBack(rows);
    }

    private void showWired() {
        page = "wired";
        LinearLayout rows = pageRows("Wired network", "Ethernet connection.");
        android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        android.net.NetworkCapabilities caps = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        boolean ethernet = caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET);
        infoRow(rows, "Connection", ethernet ? "Ethernet active" : "No active Ethernet connection");
        connectionDetails(rows, android.net.NetworkCapabilities.TRANSPORT_ETHERNET);
        hubRow(rows,R.drawable.ic_wifi,"IP settings","DHCP / static IPv4",()->SessionEthernetUi.show(this,this::showWired));

        networkBack(rows);
    }

    private void showAbout() {
        page = "about";
        LinearLayout rows = pageRows("System information", "About this projector.");
        android.os.StatFs disk = new android.os.StatFs(getFilesDir().getAbsolutePath());
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo memory = new android.app.ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(memory);
        infoRow(rows, "Equipment model", "Magcubic HY320 Pro");
        infoRow(rows, "UI version", android.os.Build.ID);
        infoRow(rows, "Android version", android.os.Build.VERSION.RELEASE + " / SDK " + android.os.Build.VERSION.SDK_INT);
        infoRow(rows, "Running memory", manager == null ? "Unavailable" : android.text.format.Formatter.formatFileSize(this, memory.totalMem) + " total / " + android.text.format.Formatter.formatFileSize(this, memory.availMem) + " available");
        infoRow(rows, "Storage space", android.text.format.Formatter.formatFileSize(this, disk.getTotalBytes()) + " total / " + android.text.format.Formatter.formatFileSize(this, disk.getAvailableBytes()) + " available");
        TextView mac=infoRow(rows,"Wireless MAC",SettingsSessionClient.connected()?"Reading...":"ADB helper required");
        TextView serial=infoRow(rows,"Serial number",SettingsSessionClient.connected()?"Reading...":"ADB helper required");
        int generation=pageGeneration;
        if(SettingsSessionClient.connected())SessionSettingsUi.read(this,SettingsSessionClient.IDENTIFIERS,new Bundle(),result->{if(generation==pageGeneration){mac.setText(result.getString("mac"));serial.setText(result.getString("serial"));}});
        stockOption(rows, "Local upgrade", "Firmware installation remains under manufacturer control. Opening settings does not begin an upgrade. Only use an update intended for this exact unit.", "System information");
        stockOption(rows, "Online upgrade", "Use the manufacturer's online updater through System information. CleanYourTV does not download or install firmware.", "System information");
        backRow(rows);
        focusFirst(rows);
    }

    @Override public void onBackPressed() {
        if ("bluetooth-search".equals(page)) showBluetooth();
        else if ("keyboards".equals(page)) showLanguage();
        else if ("app-permissions".equals(page)) showApp(selectedApp);
        else if ("app".equals(page)) showApps();
        else if ("wifi-networks".equals(page)) showWifi();
        else if ("wifi".equals(page) || "hotspot".equals(page) || "wired".equals(page)) showNetwork();
        else if ("developer".equals(page) || "appearance".equals(page) || "sound".equals(page) || "storage".equals(page)) showOther();
        else if (!"hub".equals(page)) showHub();
        else super.onBackPressed();
    }

    private android.media.AudioManager audio() {
        return (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
    }

    private String audioOutputs() {
        android.media.AudioManager audio = audio();
        if (audio == null) return "Unavailable";
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (android.media.AudioDeviceInfo device : audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (device.getType()) {
                case android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: names.add("Projector speaker"); break;
                case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                case android.media.AudioDeviceInfo.TYPE_BLE_HEADSET:
                case android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER: names.add("Bluetooth audio"); break;
                case android.media.AudioDeviceInfo.TYPE_USB_DEVICE:
                case android.media.AudioDeviceInfo.TYPE_USB_HEADSET: names.add("USB audio"); break;
                case android.media.AudioDeviceInfo.TYPE_HDMI: names.add("HDMI audio"); break;
                case android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET: names.add("Wired headphones"); break;
                default: break;
            }
        }
        return names.isEmpty() ? "Managed by the projector" : android.text.TextUtils.join("\n", names);
    }

    private void showBluetooth() {
        page = "bluetooth";
        LinearLayout rows = pageRows("Bluetooth", "Headphones, speakers and accessories.");
        bluetoothPowerRow(rows);
        hubRow(rows, R.drawable.settings_bluetooth, "Search for Bluetooth devices", "Search here", this::showBluetoothSearch);
        infoRow(rows, "Available audio outputs", audioOutputs());
        boolean canRead = android.os.Build.VERSION.SDK_INT < 31
                || checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        if (!canRead) {
            hubRow(rows, R.drawable.ic_settings, "Show paired devices", "Allow Nearby devices", () ->
                    requestPermissions(new String[] {android.Manifest.permission.BLUETOOTH_CONNECT}, PAIRED_DEVICES_PERMISSION));
        } else {
            android.bluetooth.BluetoothManager manager = (android.bluetooth.BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            android.bluetooth.BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            try {
                if (adapter == null) infoRow(rows, "Bluetooth", "Unavailable");
                else {
                    infoRow(rows, "Bluetooth", adapter.isEnabled() ? "On" : "Off");
                    List<String> names = new ArrayList<>();
                    for (android.bluetooth.BluetoothDevice device : adapter.getBondedDevices()) {
                        String name = device.getName();
                        names.add(name == null || name.trim().isEmpty() ? "Paired accessory" : name);
                    }
                    names.sort(String.CASE_INSENSITIVE_ORDER);
                    infoRow(rows, "Paired devices", names.isEmpty() ? "No paired devices" : android.text.TextUtils.join("\n", names));
                }
            } catch (SecurityException ignored) {
                infoRow(rows, "Paired devices", "Access unavailable. Use device controls below.");
            }
        }
        hubRow(rows, R.drawable.settings_bluetooth, "Available devices", "On-demand search", this::showBluetoothSearch);
        hubRow(rows,R.drawable.settings_bluetooth,"Manage paired devices","Connections & pairings",()->{
            if(SettingsSessionClient.connected()){SessionDevicesUi.show(this);return;}
            if(bluetoothAudio!=null)bluetoothAudio.close();
            try{bluetoothAudio=new BluetoothAudioUi(this);bluetoothAudio.show();}
            catch(IllegalStateException|SecurityException unavailable){bluetoothAudio=null;Toast.makeText(this,"Bluetooth audio controls are unavailable on this firmware. No connection changed.",Toast.LENGTH_LONG).show();}
        });

        backRow(rows);
        for (int i = 0; i < rows.getChildCount(); i++) {
            if (rows.getChildAt(i).isFocusable()) { rows.getChildAt(i).requestFocus(); break; }
        }
    }

    /** Use Android's public consent dialog; never call restricted adapter enable/disable APIs. */
    private void bluetoothPowerRow(LinearLayout rows) {
        if (android.os.Build.VERSION.SDK_INT >= 31
                && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            hubRow(rows, R.drawable.settings_bluetooth, "Bluetooth power", "Allow Nearby devices", () ->
                    requestPermissions(new String[] {android.Manifest.permission.BLUETOOTH_CONNECT}, PAIRED_DEVICES_PERMISSION));
            return;
        }
        android.bluetooth.BluetoothManager manager = (android.bluetooth.BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        android.bluetooth.BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) { infoRow(rows, "Bluetooth power", "Unavailable"); return; }
        try {
            int state = adapter.getState();
            if (state == android.bluetooth.BluetoothAdapter.STATE_OFF) {
                hubRow(rows,R.drawable.settings_bluetooth,"Bluetooth power","Off",()->SessionSettingsUi.power(this,true,this::showBluetooth));
            } else if (state == android.bluetooth.BluetoothAdapter.STATE_ON) {
                hubRow(rows,R.drawable.settings_bluetooth,"Bluetooth power","On",()->SessionSettingsUi.power(this,true,this::showBluetooth));
            } else {
                infoRow(rows, "Bluetooth power", state == android.bluetooth.BluetoothAdapter.STATE_TURNING_ON
                        ? "Turning on. Reopen this page to refresh." : "Turning off. Reopen this page to refresh.");
            }
        } catch (SecurityException e) {
            infoRow(rows, "Bluetooth power", "Access unavailable. Use Manage Bluetooth devices below.");
        }
    }

    private void showBluetoothSearch() {
        page = "bluetooth-search";
        LinearLayout rows = pageRows("Bluetooth search", "Find an accessory.");
        scanStatus = infoRow(rows, "Search", "Ready");
        hubRow(rows, R.drawable.settings_refresh, "Search again", "12 seconds maximum", this::beginBluetoothScan);
        hubRow(rows, R.drawable.settings_power, "Stop search", "", this::stopBluetoothScan);
        hubRow(rows, R.drawable.settings_back, "Back to Bluetooth", "", this::showBluetooth);
        scanResults = new LinearLayout(this);
        scanResults.setOrientation(LinearLayout.VERTICAL);
        rows.addView(scanResults);
        focusFirst(rows);
        beginBluetoothScan();
    }

    private void beginBluetoothScan() {
        boolean wasSearching = ownsDiscovery;
        stopBluetoothScan();
        if (wasSearching) {
            if (scanStatus != null) scanStatus.setText("Search stopped. Select Search again to start a fresh search.");
            return;
        }
        if (!"bluetooth-search".equals(page) || scanResults == null) return;
        if (android.os.Build.VERSION.SDK_INT < 31) {
            scanStatus.setText("Use device Bluetooth controls on this Android version."); return;
        }
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT}, SCAN_PERMISSION); return;
        }
        android.bluetooth.BluetoothManager manager = (android.bluetooth.BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        scanningAdapter = manager == null ? null : manager.getAdapter();
        try {
            if (scanningAdapter == null || !scanningAdapter.isEnabled()) { scanStatus.setText("Bluetooth is off or unavailable. Use device controls to enable it."); return; }
            if (scanningAdapter.isDiscovering()) {
                scanningAdapter = null;
                scanStatus.setText("Another Bluetooth search is active. Try again after it finishes.");
                return;
            }
            scanResults.removeAllViews();
            discoveredAddresses.clear();
            discoveryReceiver = new android.content.BroadcastReceiver() {
                @Override public void onReceive(android.content.Context context, Intent intent) {
                    if (!"bluetooth-search".equals(page)) return;
                    if (android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                        stopBluetoothScan(); return;
                    }
                    if (!android.bluetooth.BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) return;
                    android.bluetooth.BluetoothDevice device = intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE);
                    if (device == null || scanResults == null) return;
                    try {
                        if (!discoveredAddresses.add(device.getAddress())) return;
                        String name = device.getName();
                        if (name == null || name.trim().isEmpty()) name = "Unnamed Bluetooth device";
                        final String label = name;
                        hubRow(scanResults, R.drawable.settings_bluetooth, label,
                                device.getBondState() == android.bluetooth.BluetoothDevice.BOND_BONDED ? "Paired" : "Pair", () -> {
                                    stopBluetoothScan();
                                    if (device.getBondState() == android.bluetooth.BluetoothDevice.BOND_BONDED) {
                                        Toast.makeText(LauncherSettingsActivity.this, "Already paired. Open Manage paired devices to manage supported media audio.", Toast.LENGTH_LONG).show(); return;
                                    }
                                    AlertDialog dialog = new SettingsPopup.Builder(LauncherSettingsActivity.this).setTitle("Pair with " + label + "?")
                                            .setMessage("Pair only this accessory. Android handles confirmation and may connect it after successful pairing.")
                                            .setPositiveButton("Pair", (d, which) -> {
                                                try {
                                                    if (!device.createBond()) Toast.makeText(LauncherSettingsActivity.this, "Pairing could not start. Use device Bluetooth controls.", Toast.LENGTH_LONG).show();
                                                } catch (SecurityException e) { Toast.makeText(LauncherSettingsActivity.this, "Pairing permission is unavailable.", Toast.LENGTH_LONG).show(); }
                                            }).setNegativeButton(android.R.string.cancel, null).create();
                                    styleDialog(dialog);
                                });
                    } catch (SecurityException ignored) { scanStatus.setText("Bluetooth access was revoked."); stopBluetoothScan(); }
                }
            };
            android.content.IntentFilter filter = new android.content.IntentFilter(android.bluetooth.BluetoothDevice.ACTION_FOUND);
            filter.addAction(android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(discoveryReceiver, filter, android.content.Context.RECEIVER_EXPORTED);
            else registerReceiver(discoveryReceiver, filter);
            discoveryRegistered = true;
            if (!scanningAdapter.startDiscovery()) { scanStatus.setText("Search could not start. Try device Bluetooth controls."); stopBluetoothScan(); return; }
            ownsDiscovery = true;
            scanStatus.setText("Searching...");
            scanHandler.postDelayed(this::stopBluetoothScan, 12000);
        } catch (SecurityException e) { if (scanStatus != null) scanStatus.setText("Bluetooth search permission is unavailable."); stopBluetoothScan(); }
    }

    private void stopBluetoothScan() {
        scanHandler.removeCallbacksAndMessages(null);
        if (scanningAdapter != null) {
            try { if (ownsDiscovery && scanningAdapter.isDiscovering()) scanningAdapter.cancelDiscovery(); } catch (SecurityException ignored) { }
            scanningAdapter = null;
        }
        ownsDiscovery = false;
        if (discoveryReceiver != null) {
            if (discoveryRegistered) unregisterReceiver(discoveryReceiver);
            discoveryRegistered = false;
            discoveryReceiver = null;
        }
        if (scanStatus != null && "Searching...".contentEquals(scanStatus.getText())) scanStatus.setText("Search stopped. " + discoveredAddresses.size() + " device(s) found.");
    }

    @Override protected void onStop() {
        SessionDebugUi.close();
        if(bluetoothAudio!=null){bluetoothAudio.close();bluetoothAudio=null;}
        if (orientationPilot != null) { orientationPilot.close(); orientationPilot = null; }
        if (manualCornerPreview != null) { manualCornerPreview.close(); manualCornerPreview = null; }
        if (projectionReset != null) { projectionReset.close(); projectionReset = null; }
        if (projectionScale != null) { projectionScale.close(); projectionScale = null; }
        if (automaticProjection != null) { automaticProjection.close(); automaticProjection = null; }
        stopBluetoothScan();
        stopWifiScan();
        super.onStop();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PAIRED_DEVICES_PERMISSION && "bluetooth".equals(page)) showBluetooth();
        if (requestCode == SCAN_PERMISSION && "bluetooth-search".equals(page)) {
            boolean granted = grantResults.length > 0;
            for (int result : grantResults) granted &= result == PackageManager.PERMISSION_GRANTED;
            if (granted) beginBluetoothScan();
            else if (scanStatus != null) scanStatus.setText("Search permission was not granted.");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!returningFromDeviceControls) return;
        returningFromDeviceControls = false;
        if ("wifi-networks".equals(page)) showWifiNetworks();
        else if ("wifi".equals(page)) showWifi();
        else if ("wired".equals(page)) showWired();
        else if ("about".equals(page)) showAbout();
        else if ("bluetooth".equals(page)) showBluetooth();
        else if ("network".equals(page)) showNetwork();
        else if ("datetime".equals(page)) showDateTime();
        else if ("sound".equals(page)) showSound();
        else if ("keyboards".equals(page)) manageKeyboards();
        else if ("storage".equals(page)) showStorage();
        else if ("app".equals(page) && selectedApp != null) showApp(selectedApp);
    }

    private void showSound() {
        page = "sound";
        LinearLayout rows = pageRows("Sound", "Volume and audio output.");
        android.media.AudioManager audio = audio();
        if (audio != null) {
            soundVolume = infoRow(rows, "Media volume", audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                    + " / " + audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC));
            soundMute = infoRow(rows, "Mute", audio.isStreamMute(android.media.AudioManager.STREAM_MUSIC) ? "On" : "Off");
        }
        if (audio != null && !audio.isVolumeFixed()) {
            hubRow(rows, R.drawable.settings_volume, "Set media volume", "Choose level", this::showVolumePicker);
            hubRow(rows, R.drawable.settings_volume, "Toggle mute", "Media only", () -> {
                try {
                    audio.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                            audio.isStreamMute(android.media.AudioManager.STREAM_MUSIC) ? android.media.AudioManager.ADJUST_UNMUTE : android.media.AudioManager.ADJUST_MUTE, 0);
                    showSound();
                } catch (SecurityException e) { Toast.makeText(this, "Audio adjustment was refused.", Toast.LENGTH_LONG).show(); }
            });
        }
        infoRow(rows, "Available outputs", audioOutputs());
        hubRow(rows, R.drawable.ic_settings, "Bluetooth audio", "Speakers & headphones", this::showBluetooth);
        // Volume, mute and output status are already provided on this local page.
        backRow(rows);
        rows.getChildAt(rows.getChildCount() - 1).requestFocus();
    }

    private void showVolumePicker() {
        android.media.AudioManager audio = audio();
        if (audio == null || audio.isVolumeFixed()) return;
        int maximum = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        android.widget.SeekBar slider = new android.widget.SeekBar(this);
        slider.setMax(maximum);
        slider.setProgress(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));
        slider.setKeyProgressIncrement(1);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(16), dp(24), dp(16));
        TextView value = new TextView(this);
        value.setTextSize(20); value.setTextColor(0xFFF5F5FA);
        value.setTypeface(getResources().getFont(R.font.outfit_regular));
        value.setText(slider.getProgress() + " / " + maximum);
        content.addView(value); content.addView(slider);
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) { value.setText(progress + " / " + maximum); }
            @Override public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar bar) { }
        });
        AlertDialog dialog = new SettingsPopup.Builder(this).setTitle("Media volume").setView(content)
                .setPositiveButton("Apply", (d, which) -> {
                    try { audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, slider.getProgress(), 0); }
                    catch (SecurityException e) { Toast.makeText(this, "Audio adjustment was refused.", Toast.LENGTH_LONG).show(); }
                    showSound();
                }).setNegativeButton(android.R.string.cancel, null).create();
        styleDialog(dialog);
        slider.requestFocus();
    }

    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        boolean handled = super.dispatchKeyEvent(event);
        int key = event.getKeyCode();
        if ("sound".equals(page) && event.getAction() == android.view.KeyEvent.ACTION_UP
                && (key == android.view.KeyEvent.KEYCODE_VOLUME_UP || key == android.view.KeyEvent.KEYCODE_VOLUME_DOWN
                || key == android.view.KeyEvent.KEYCODE_VOLUME_MUTE)) {
            // Observe the normal volume path after it completes; never consume or remap the key.
            soundHandler.postDelayed(() -> {
                android.media.AudioManager audio = audio();
                if (audio == null || soundVolume == null || soundMute == null) return;
                soundVolume.setText(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                        + " / " + audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC));
                soundMute.setText(audio.isStreamMute(android.media.AudioManager.STREAM_MUSIC) ? "On" : "Off");
            }, 150);
        }
        return handled;
    }

    private static final int FOLDER_REQUEST=51;
    private FolderBrowserUi folderBrowser;
    private void showFiles(){
        if(folderBrowser!=null)folderBrowser.close();
        String saved=getSharedPreferences("usb_browser",MODE_PRIVATE).getString("tree",null);
        android.net.Uri root=null;
        if(saved!=null)try{android.net.Uri tree=android.net.Uri.parse(saved);root=android.provider.DocumentsContract.buildDocumentUriUsingTree(tree,android.provider.DocumentsContract.getTreeDocumentId(tree));}catch(RuntimeException invalid){/* Ask for a new folder. */}
        folderBrowser=new FolderBrowserUi(this,()->{
            Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try{startActivityForResult(picker,FOLDER_REQUEST);}catch(ActivityNotFoundException unavailable){Toast.makeText(this,"Folder selection is unavailable on this firmware.",Toast.LENGTH_LONG).show();}
        },()->{
            String tree=getSharedPreferences("usb_browser",MODE_PRIVATE).getString("tree",null);
            if(tree!=null)try{getContentResolver().releasePersistableUriPermission(android.net.Uri.parse(tree),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
            getSharedPreferences("usb_browser",MODE_PRIVATE).edit().remove("tree").apply();showFiles();
        });folderBrowser.show(root);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==FOLDER_REQUEST&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            android.net.Uri tree=data.getData();
            try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String old=getSharedPreferences("usb_browser",MODE_PRIVATE).getString("tree",null);
                getSharedPreferences("usb_browser",MODE_PRIVATE).edit().putString("tree",tree.toString()).apply();
                if(old!=null&&!old.equals(tree.toString()))try{getContentResolver().releasePersistableUriPermission(android.net.Uri.parse(old),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
                showFiles();
            }catch(SecurityException denied){Toast.makeText(this,"Folder access was not granted.",Toast.LENGTH_LONG).show();}
        }
    }

    private void showStorage() {
        page = "storage";
        LinearLayout rows = pageRows("Storage", "Storage and USB drives.");
        android.os.StatFs disk = new android.os.StatFs(getFilesDir().getAbsolutePath());
        infoRow(rows, "Internal storage", android.text.format.Formatter.formatFileSize(this, disk.getTotalBytes()));
        infoRow(rows, "Available", android.text.format.Formatter.formatFileSize(this, disk.getAvailableBytes()));
        infoRow(rows, "Used", android.text.format.Formatter.formatFileSize(this, disk.getTotalBytes() - disk.getFreeBytes()));
        hubRow(rows,R.drawable.settings_usb,"Files & USB","Browse a chosen folder",this::showFiles);
        hubRow(rows, R.drawable.ic_apps, "Manage apps", "App information", this::showApps);
        backRow(rows);
        rows.getChildAt(3).requestFocus();
    }

    private void showDateTime() {
        page = "datetime";
        LinearLayout rows = pageRows("Date and time", "Date, time and clock format.");
        java.util.Date now = new java.util.Date();
        automaticTimeRow(rows, "Auto time", AutomaticTimeControl.Key.TIME);
        automaticTimeRow(rows, "Automatic time zone", AutomaticTimeControl.Key.ZONE);
        String auto = Settings.Global.getInt(getContentResolver(), Settings.Global.AUTO_TIME, 0) == 1 ? "On" : "Off";
        String[] labels = {"Auto time", "Date", "Time", "Time zone", "Use 24-hour format"};
        String[] values = {auto, android.text.format.DateFormat.getLongDateFormat(this).format(now), android.text.format.DateFormat.getTimeFormat(this).format(now), java.util.TimeZone.getDefault().getID(), android.text.format.DateFormat.is24HourFormat(this) ? "On / 24-hour" : "Off / 12-hour"};
        String[] details = {"Enable or disable automatic date and time.", "Choose year, month and day. Manual changes are unavailable while Auto time is on.", "Choose hour and minute. Manual changes are unavailable while Auto time is on.", "Choose the projector's time zone.", "Choose a 12-hour or 24-hour clock."};
        Intent dateSettings = new Intent(Settings.ACTION_DATE_SETTINGS);
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            if ("Auto time".equals(label)) continue;
            if ("Use 24-hour format".equals(label)) standardPreferenceRow(rows, label, "time_12_24", new String[] {"12", "24"}, new String[] {"12-hour", "24-hour"}, this::showDateTime);
            else hubRow(rows, SettingsIcons.forLabel(label, R.drawable.settings_clock), label, values[i], () -> SessionSettingsUi.clock(this,label,this::showDateTime));
        }
        backRow(rows);
        focusFirst(rows);
    }

    private void automaticTimeRow(LinearLayout rows, String label, AutomaticTimeControl.Key key) {
        String current = Settings.Global.getString(getContentResolver(), key.setting);
        String status = "1".equals(current) ? "On" : "0".equals(current) ? "Off" : "System default";
        hubRow(rows, R.drawable.settings_clock, label, status, () -> {
            if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Automatic-time access is not enabled. No value changed.", Toast.LENGTH_LONG).show();
                return;
            }
            final int[] choice = {"1".equals(current) ? 1 : "0".equals(current) ? 0 : -1};
            SharedPreferences undo = getSharedPreferences("automatic_time_undo", MODE_PRIVATE);
            AlertDialog.Builder builder = new SettingsPopup.Builder(this).setTitle(label)
                    .setSingleChoiceItems(new String[] {"Off", "On"}, choice[0], (d, which) -> choice[0] = which)
                    .setPositiveButton("Apply", (d, which) -> {
                        if (choice[0] >= 0) saveAutomaticTime(key, current, choice[0] == 1, false);
                    }).setNegativeButton(android.R.string.cancel, null);
            if (key == AutomaticTimeControl.Key.ZONE && "Projector_20260713.153921".equals(android.os.Build.ID)) {
                // Verified from this firmware's detector capabilities, not inferred from Wi-Fi or model name.
                TextView note = new TextView(this);
                note.setText("Automatic time-zone detection is unavailable on this projector.");
                note.setTextSize(18);
                note.setTextColor(0xFFF5F5FA);
                note.setTypeface(getResources().getFont(R.font.outfit_regular));
                int pad = (int) (24 * getResources().getDisplayMetrics().density);
                note.setPadding(pad, pad / 2, pad, pad / 2);
                builder.setView(note);
            }
            if (undo.getBoolean(key.name() + ".recorded", false))
                builder.setNeutralButton("Restore original", (d, which) -> saveAutomaticTime(key, current, false, true));
            styleDialog(builder.create());
        });
    }

    private void saveAutomaticTime(AutomaticTimeControl.Key key, String reviewed, boolean enabled, boolean restore) {
        int generation = pageGeneration;
        pageLoader.execute(() -> {
            String error = null;
            try {
                if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") != PackageManager.PERMISSION_GRANTED)
                    throw new SecurityException("Automatic-time access was refused.");
                SharedPreferences undo = getSharedPreferences("automatic_time_undo", MODE_PRIVATE);
                AutomaticTimeControl control = new AutomaticTimeControl(new AutomaticTimeControl.Backend() {
                    @Override public String read(AutomaticTimeControl.Key item) {
                        return Settings.Global.getString(getContentResolver(), item.setting);
                    }
                    @Override public boolean write(AutomaticTimeControl.Key item, String value) {
                        return Settings.Global.putString(getContentResolver(), item.setting, value);
                    }
                }, new AutomaticTimeControl.Journal() {
                    @Override public boolean recorded(AutomaticTimeControl.Key item) {
                        return undo.getBoolean(item.name() + ".recorded", false);
                    }
                    @Override public String original(AutomaticTimeControl.Key item) {
                        return undo.getBoolean(item.name() + ".present", false) ? undo.getString(item.name(), null) : null;
                    }
                    @Override public void capture(AutomaticTimeControl.Key item, String original) {
                        if (recorded(item)) return;
                        if (!undo.edit().putBoolean(item.name() + ".recorded", true)
                                .putBoolean(item.name() + ".present", original != null)
                                .putString(item.name(), original).commit())
                            throw new IllegalStateException("Could not save the original setting. Nothing changed.");
                    }
                });
                if (restore) control.restore(key, reviewed); else control.set(key, reviewed, enabled);
            } catch (RuntimeException failure) {
                error = failure instanceof SecurityException ? "Automatic-time access was refused. Reopen to check its state." : failure.getMessage();
            }
            final String message = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                if (generation == pageGeneration && "datetime".equals(page)) showDateTime();
            });
        });
    }

    private boolean projectionRecoveryPending() {
        return new OrientationJournal(getFilesDir()).exists() || new OrientationJournal(getFilesDir(),OrientationJournal.Scope.MANUAL_CORNERS).exists()
                || new OrientationJournal(getFilesDir(),OrientationJournal.Scope.RESET).exists()
                || new OrientationJournal(getFilesDir(),OrientationJournal.Scope.SCALING).exists()
                || AutomaticProjectionJournal.pending(getFilesDir());
    }
    private void autoFocusRow(LinearLayout rows) {
        try {
            boolean current=ProjectorAutoFocus.read();
            hubRow(rows,R.drawable.settings_focus,"Auto Focus",current?"On":"Off",()->{
                final int[] choice={current?1:0};
                AlertDialog.Builder builder=new SettingsPopup.Builder(this).setTitle("Auto Focus")
                        .setSingleChoiceItems(new String[]{"Off","On"},choice[0],(d,index)->choice[0]=index)
                        .setPositiveButton("Apply",(d,w)->saveAutoFocus(choice[0]==1))
                        .setNegativeButton(android.R.string.cancel,null);
                Boolean original=ProjectorAutoFocus.original(this);
                if(original!=null)builder.setNeutralButton("Restore original",(d,w)->saveAutoFocus(original));
                styleDialog(builder.create());
            });
        } catch(Exception unavailable) {
            stockOption(rows,"Auto Focus","Enable or disable automatic optical focus.","Projection settings");
        }
    }
    private void saveAutoFocus(boolean enabled) {
        int generation=pageGeneration;
        pageLoader.execute(()->{
            String problem=null;
            try {ProjectionWorkflows.prepare(this,null);ProjectorAutoFocus.set(this,enabled);}catch(Exception error){android.util.Log.w("ProjectorFocus","Preference change failed",error);problem=ProjectionWorkflows.message(error);}
            final String message=problem;
            runOnUiThread(()->{
                if(isFinishing() || isDestroyed() || generation!=pageGeneration)return;
                if(message!=null)Toast.makeText(this,message,Toast.LENGTH_LONG).show();
                showProjector();
            });
        });
    }

    private void showProjector() {
        page = "projector";
        LinearLayout rows = pageRows("Projection settings", "Make the picture fit your space.");

        if (getResources().getBoolean(R.bool.orientation_pilot_entry_enabled)) {
            hubRow(rows, R.drawable.settings_rotate, "Installation mode", "Front, rear & ceiling", () -> {
                if (orientationPilot != null) orientationPilot.close();
                orientationPilot = new OrientationPilotUi(this);
                orientationPilot.show();
            });
        }
        hubRow(rows,R.drawable.settings_scale,"Digital scaling","Steps 0 - 20",()->{
            if(projectionScale!=null)projectionScale.close();projectionScale=new ProjectionScaleUi(this);projectionScale.show();
        });
        hubRow(rows,R.drawable.settings_keystone,"Auto Trapezoidal Correction","Reading...",()->{
            if(automaticProjection!=null)automaticProjection.close();automaticProjection=new AutomaticProjectionUi(this,false);automaticProjection.show();
        });
        autoFocusRow(rows);
        hubRow(rows,R.drawable.settings_focus,"One-Touch Calibration","Refocus",()->{
            if(automaticProjection!=null)automaticProjection.close();automaticProjection=new AutomaticProjectionUi(this,true);automaticProjection.show();
        });
        hubRow(rows, R.drawable.settings_keystone, "Manual trapezoidal", "Four corners", () -> {
            if (manualCornerPreview != null) manualCornerPreview.close();
            manualCornerPreview = new ManualCornerUi(this);
            manualCornerPreview.show();
        });

        hubRow(rows,R.drawable.settings_refresh,"Reset trapezoidal correction","Reset",()->{
            if(projectionReset!=null)projectionReset.close();projectionReset=new ProjectionResetUi(this);projectionReset.show();
        });
        backRow(rows);
        focusFirst(rows);
        loadProjectionReadouts(rows);
    }

    void projectionSettingSaved(){
        if(isFinishing()||isDestroyed()||!"projector".equals(page))return;
        LinearLayout rows=findViewById(R.id.settingsHubRows);if(rows!=null)loadProjectionReadouts(rows);
    }
    private void loadProjectionReadouts(LinearLayout rows){
        int generation=pageGeneration;
        pageLoader.execute(()->{
            ProjectionReadouts data=null;try{data=ProjectionReadouts.read();}catch(Exception failure){android.util.Log.w("ProjectionStatus","Read-only status unavailable",failure);}
            final ProjectionReadouts result=data;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||generation!=pageGeneration||!"projector".equals(page))return;
                String[][] values=result==null?new String[][]{{"Auto Trapezoidal Correction","Unavailable"},{"One-Touch Calibration","Unavailable"}}:
                    new String[][]{{"Installation mode",result.mode()},{"Digital scaling",result.scaling()},{"Auto Trapezoidal Correction",ProjectionReadouts.toggle(result.state.guard(OrientationPilot.Guard.AUTO))},{"One-Touch Calibration","Refocus"}};
                for(String[] item:values){View row=rows.findViewWithTag(item[0]);if(row!=null)((TextView)row.findViewById(R.id.status)).setText(item[1]);}
            });
        });
    }
    private void showApps() {
        page = "apps";
        LinearLayout rows = pageRows("Application Manager", "Installed applications.");
        backRow(rows);
        rows.getChildAt(0).requestFocus();
        int generation = pageGeneration;
        pageLoader.execute(() -> {
            PackageManager pm = getPackageManager();
            java.util.Map<String, String[]> found = new java.util.TreeMap<>();
            for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                for (ResolveInfo item : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                    String pkg = item.activityInfo.packageName;
                    // Do not turn factory/production tools into discoverable settings shortcuts.
                    if (pkg.equals("com.softwinner.tv.factorymenu") || pkg.equals("com.htc.hyk_test")) continue;
                    if (!found.containsKey(pkg)) found.put(pkg, new String[] {item.loadLabel(pm).toString(), pkg});
                }
            }
            List<String[]> apps = new ArrayList<>(found.values());
            apps.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != pageGeneration) return;
                for (String[] app : apps) {
                    hubRow(rows, R.drawable.ic_apps, app[0], "App information", () -> showApp(app[1]));
                    if (app[1].equals(selectedApp)) rows.getChildAt(rows.getChildCount() - 1).requestFocus();
                }
            });
        });
    }

    private String size(long bytes){return android.text.format.Formatter.formatFileSize(this,bytes);}

    private void showApp(String pkg) {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(pkg, 0);
            selectedApp = pkg;
            page = "app";
            LinearLayout rows = pageRows(getPackageManager().getApplicationLabel(info.applicationInfo).toString(),
                    "Application information.");
            infoRow(rows, "Version", info.versionName == null ? "Not supplied" : info.versionName);
            infoRow(rows, "Package", pkg);
            infoRow(rows, "Installation", (info.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                    ? "Preinstalled application" : "User-installed application");
            hubRow(rows, R.drawable.settings_info, "Permissions", "View requested access", () -> showAppPermissions(pkg));
            Intent settings = SystemSettings.appInfo(this, pkg);

            infoRow(rows, "Base APK", new File(info.applicationInfo.sourceDir).length() > 0
                    ? android.text.format.Formatter.formatFileSize(this, new File(info.applicationInfo.sourceDir).length()) + " APK (app data excluded)" : "See device app options");
            TextView storage=infoRow(rows,"App storage","Loading...");
            int generation=pageGeneration;
            pageLoader.execute(()->{
                String result;
                try{android.app.usage.StorageStatsManager manager=getSystemService(android.app.usage.StorageStatsManager.class);
                    android.app.usage.StorageStats stats=manager.queryStatsForPackage(info.applicationInfo.storageUuid,pkg,android.os.Process.myUserHandle());
                    result="App: "+size(stats.getAppBytes())+"\nData: "+size(Math.max(0,stats.getDataBytes()-stats.getCacheBytes()))+"\nCache: "+size(stats.getCacheBytes())+"\nTotal: "+size(stats.getAppBytes()+stats.getDataBytes());
                }catch(Exception unavailable){result="Storage information unavailable.";}
                final String value=result;runOnUiThread(()->{if(generation==pageGeneration&&!isFinishing()&&!isDestroyed())storage.setText(value);});
            });
            if((info.applicationInfo.flags&android.content.pm.ApplicationInfo.FLAG_SYSTEM)==0&&!pkg.equals(getPackageName())){
                hubRow(rows,R.drawable.settings_refresh,"Clear cache","Temporary files only",()->SessionSettingsUi.appAction(this,pkg,SettingsSessionClient.CACHE,()->showApp(pkg)));
                hubRow(rows,R.drawable.settings_delete,"Clear app data","Erases this app's accounts and settings",()->SessionSettingsUi.appAction(this,pkg,SettingsSessionClient.DATA,()->showApp(pkg)));
                hubRow(rows,R.drawable.settings_power,"Force stop","Stop this app",()->SessionSettingsUi.appAction(this,pkg,SettingsSessionClient.FORCE_STOP,()->showApp(pkg)));
            }
            boolean systemApp = (info.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
            if (!systemApp && !pkg.equals(getPackageName()))
                hubRow(rows, R.drawable.ic_apps, "Uninstall", "Android confirmation", () -> requestAppRemoval(pkg));
            else infoRow(rows, "Uninstall", "This launcher and preinstalled components are protected here.");
            hubRow(rows, R.drawable.ic_apps, "Back to apps", "", this::showApps);
            rows.getChildAt(rows.getChildCount() - 1).requestFocus();
        } catch (PackageManager.NameNotFoundException e) {
            showApps();
        }
    }

    @Override protected void onDestroy() {
        pageGeneration++;
        if(folderBrowser!=null)folderBrowser.close();
        soundHandler.removeCallbacksAndMessages(null);
        stopBluetoothScan();
        stopWifiScan();
        pageLoader.shutdownNow();
        super.onDestroy();
    }

    private void showAppPermissions(String pkg) {
        page = "app-permissions";
        selectedApp = pkg;
        LinearLayout rows = pageRows("Permissions", "Application permissions.");
        hubRow(rows, R.drawable.settings_back, "Back to application", "", () -> showApp(pkg));
        focusFirst(rows);
        int generation = pageGeneration;
        pageLoader.execute(() -> {
            List<String[]> permissions = new ArrayList<>();
            try {
                android.content.pm.PackageInfo app = getPackageManager().getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);
                if (app.requestedPermissions != null) for (int i = 0; i < app.requestedPermissions.length; i++) {
                    String name = app.requestedPermissions[i], label = name;
                    try { label = getPackageManager().getPermissionInfo(name, 0).loadLabel(getPackageManager()).toString(); }
                    catch (PackageManager.NameNotFoundException | RuntimeException unknown) {
                        // This firmware has malformed permission resource records; keep the real name.
                    }
                    boolean granted = app.requestedPermissionsFlags != null && i < app.requestedPermissionsFlags.length
                            && (app.requestedPermissionsFlags[i] & android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                    permissions.add(new String[] {label, name, granted ? "Granted" : "Not granted"});
                }
            } catch (PackageManager.NameNotFoundException removed) {
                runOnUiThread(() -> { if (generation == pageGeneration && !isFinishing()) showApps(); });
                return;
            }
            permissions.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != pageGeneration) return;
                if (permissions.isEmpty()) infoRow(rows, "Permissions", "No requested permissions reported by Android.");
                for (String[] item : permissions)
                    hubRow(rows,R.drawable.settings_info,item[0],item[2],()->SessionSettingsUi.permission(this,pkg,item[1],item[0],()->showAppPermissions(pkg)));
            });
        });
    }

    private void requestAppRemoval(String pkg) {
        try {
            android.content.pm.ApplicationInfo info = getPackageManager().getApplicationInfo(pkg, 0);
            if (pkg.equals(getPackageName()) || (info.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
                throw new SecurityException("Protected application");
            Intent confirmation = new Intent(Intent.ACTION_DELETE, android.net.Uri.fromParts("package", pkg, null));
            // Platform owns consent. No DELETE_PACKAGES, shell uninstall or automatic confirmation.
            startActivity(confirmation);
            returningFromDeviceControls = true;
        } catch (PackageManager.NameNotFoundException | ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, "Android could not open removal confirmation. No app was removed.", Toast.LENGTH_LONG).show();
        }
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = "none".equals(current) ? images.size()
                : indexOf(images, current);
        new SettingsPopup.Builder(this)
                .setTitle(R.string.wallpaper_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == images.size()) {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
                    } else {
                        choices.edit().putString(Config.WALLPAPER_CHOICE_KEY,
                                images.get(which).getName()).apply();
                    }
                    changed();
                    Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .setNeutralButton(R.string.wallpaper_theme_default, (dialog, which) -> {
                    choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
                    changed();
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Off, then every app with a TV entry point by name. Saved at once; it is read on the next boot. */
    private void showOpenOnStartPicker() {
        PackageManager pm = getPackageManager();
        List<String[]> apps = new ArrayList<>();
        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(tv, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            boolean seen = false;
            for (String[] a : apps) seen |= a[1].equals(pkg);
            if (!seen) apps.add(new String[]{r.loadLabel(pm).toString(), pkg});
        }
        Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
        String[] labels = new String[apps.size() + 1];
        labels[0] = getString(R.string.off);
        String current = choices.getString(OpenOnStart.KEY, "");
        int checked = 0;
        for (int i = 0; i < apps.size(); i++) {
            labels[i + 1] = apps.get(i)[0];
            if (apps.get(i)[1].equals(current)) checked = i + 1;
        }
        new SettingsPopup.Builder(this)
                .setTitle(R.string.open_on_start_label)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) choices.edit().remove(OpenOnStart.KEY).apply();
                    else {
                        OpenOnStart.skipThisBoot(this);
                        choices.edit().putString(OpenOnStart.KEY, apps.get(which - 1)[1]).apply();
                    }
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** The picked app's name, its package when it is gone, or Off. */
    private String openOnStartLabel() {
        String pkg = choices.getString(OpenOnStart.KEY, "");
        if (pkg.isEmpty()) return getString(R.string.off);
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    /**
     * A choice was saved. Home rebuilds when it next resumes because the change count moved, whether these
     * settings are left with Back or with the remote's Home button (which cancels the activity result).
     */
    private void changed() {
        setResult(RESULT_OK);
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY,
                choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        changed();
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        for (int i = 0; i < dockChoices.length; i++) {
            dockChoices[i].setSelected(dockOpacity == DOCK_OPACITIES[i]);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        openOnStartChoice.setText(getString(R.string.open_on_start_state, openOnStartLabel()));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains(Config.DOCK_OPACITY_KEY)
                || choices.contains(OpenOnStart.KEY);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
            returningFromDeviceControls = true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
