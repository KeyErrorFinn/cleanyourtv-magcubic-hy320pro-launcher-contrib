package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/** One owner-requested foreground scan. No Wi-Fi toggles, network writes, retries or polling. */
final class WifiScanSession {
    interface Listener { void finished(String status); }
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver receiver;
    private Listener listener;
    private final Runnable timeout = () -> finish("No fresh scan arrived. Showing Android's last results.");
    WifiScanSession(Context context) { this.context = context.getApplicationContext(); }
    void start(Listener callback) {
        close();
        listener = callback;
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        try {
            if (wifi == null || !wifi.isWifiEnabled()) {
                finish("Wi-Fi is off or unavailable. No connection changed."); return;
            }
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent intent) {
                    if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction()))
                        finish(intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                                ? "Scan complete. Nearby results updated."
                                : "Android did not refresh the scan. Showing its last results.");
                }
            };
            IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            // SCAN_RESULTS is a protected broadcast; Wi-Fi can send it from a privileged non-system UID.
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            else context.registerReceiver(receiver, filter);
            handler.postDelayed(timeout, 18000);
            if (!wifi.startScan()) finish("Android declined or throttled this scan. Showing its last results.");
        } catch (SecurityException denied) {
            finish("Scan access was refused. Location permission/services may be required. No setting changed.");
        } catch (RuntimeException unavailable) {
            finish("Wi-Fi scan is unavailable. Showing Android's last results.");
        }
    }
    private void finish(String status) {
        Listener callback = listener;
        close();
        if (callback != null) callback.finished(status);
    }
    void close() {
        handler.removeCallbacks(timeout);
        if (receiver != null) {
            try { context.unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) { }
            receiver = null;
        }
        listener = null;
    }
}
