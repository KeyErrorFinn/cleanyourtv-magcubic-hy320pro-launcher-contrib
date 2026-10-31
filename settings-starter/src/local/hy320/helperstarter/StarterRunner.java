package local.hy320.helperstarter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import java.util.concurrent.atomic.AtomicBoolean;

/** One bounded startup transaction. It never changes USB debugging or authentication policy. */
final class StarterRunner {
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private final Context context;
    private volatile boolean cancelled;
    private volatile LocalAdb client;
    StarterRunner(Context context) { this.context = context.getApplicationContext(); }
    void cancel() { cancelled = true; LocalAdb c = client; if (c != null) c.close(); }
    private void check() throws InterruptedException {
        if (cancelled || Thread.currentThread().isInterrupted())
            throw new InterruptedException("Startup cancelled");
    }
    String run(boolean boot, boolean automatic) throws Exception {
        if (!BUSY.compareAndSet(false, true)) throw new IllegalStateException("Startup already running");
        long started = SystemClock.elapsedRealtime();
        SharedPreferences policy = context.getSharedPreferences("startup", 0);
        int bootCount = Settings.Global.getInt(context.getContentResolver(), "boot_count", -1);
        boolean openedDebug = false;
        boolean closeAfter = false;
        boolean attachedIndependent = false;
        try {
            check();
            SignedLauncher launcher = new SignedLauncher(context);
            boolean enabled = policy.getBoolean("enabled", false);
            if (automatic && !enabled) return finish("Automatic startup is off", bootCount, boot, started, false);
            SharedPreferences identity = context.getSharedPreferences("identity", 0);
            if (!identity.getBoolean("paired", false)) throw new SecurityException("One-time pairing setup is required");
            if (Settings.Global.getInt(context.getContentResolver(), "development_settings_enabled", 0) != 1)
                throw new SecurityException("Developer options are off");
            Bundle existing = launcher.call("status", null);
            if (existing.getBoolean("connected")) {
                if (!existing.getBoolean("independent"))
                    return finish("A computer-started helper is active; debugging was left unchanged", bootCount, boot, started, false);
                attachedIndependent = true;
                closeAfter = boot && policy.getBoolean("brief_debug_at_boot", false);
                return finish("Settings helper is connected", bootCount, boot, started, false);
            }
            if (Settings.Global.getInt(context.getContentResolver(), "adb_wifi_enabled", 0) != 1) {
                boolean permitted = BootstrapContract.mayEnableDebugging(boot || !automatic, enabled || !automatic,
                        policy.getBoolean("brief_debug_at_boot", false), bootCount,
                        automatic ? policy.getInt("last_enable_boot", -1) : -1, true);
                if (!permitted) throw new IllegalStateException("Wireless debugging is off");
                String old = Settings.Global.getString(context.getContentResolver(), "adb_wifi_enabled");
                SharedPreferences.Editor record = policy.edit().putInt("last_enable_boot", bootCount);
                if (!policy.getBoolean("debug_original_recorded", false)) {
                    record.putBoolean("debug_original_recorded", true)
                            .putBoolean("debug_original_present", old != null)
                            .putString("debug_original", old);
                }
                if (!record.commit()) throw new IllegalStateException("Could not record original debugging state");
                openedDebug = true;
                if (!Settings.Global.putInt(context.getContentResolver(), "adb_wifi_enabled", 1))
                    throw new SecurityException("Android refused Wireless debugging");
            }
            check();
            int port = 0;
            for (int attempt = 0; attempt < 4; attempt++) {
                if (Settings.Global.getInt(context.getContentResolver(), "adb_wifi_enabled", 0) != 1)
                    throw new InterruptedException("Wireless debugging was switched off");
                try { port = Endpoint.port(); break; }
                catch (java.io.IOException notReady) {
                    if (attempt == 3) throw notReady;
                    Thread.sleep(1000L << attempt); check();
                }
            }
            Bundle ticket = launcher.call("request-start", null);
            if (ticket.getBoolean("connected")) {
                if (!ticket.getBoolean("independent"))
                    return finish("A computer-started helper is active; debugging was left unchanged", bootCount, boot, started, false);
                attachedIndependent = true;
                closeAfter = boot && policy.getBoolean("brief_debug_at_boot", false);
                return finish("Settings helper is connected", bootCount, boot, started, false);
            }
            String nonce = ticket.getString("nonce");
            if (!BootstrapContract.validNonce(nonce)) throw new SecurityException("Signed startup ticket unavailable");
            String pin = identity.getString("connect_server_pin", null);
            if (pin == null) throw new SecurityException("One-time service enrolment is required");
            boolean freshDaemon = BootstrapContract.needsFreshDaemonProof(
                    identity.getInt("pin_boot", -1), bootCount, boot || !automatic, enabled || !automatic);
            String received;
            try (LocalAdb local = new LocalAdb()) {
                client = local; check();
                received = local.startExistingHelper(new ClientIdentity(), port,
                        freshDaemon ? null : pin, launcher.command(nonce));
            } finally { client = null; }
            for (int attempt = 0; attempt < 16; attempt++) {
                check();
                if (Settings.Global.getInt(context.getContentResolver(), "adb_wifi_enabled", 0) != 1)
                    throw new InterruptedException("Wireless debugging was switched off");
                Bundle state = launcher.call("status", nonce);
                if (state.getBoolean("connected")) {
                    if (!state.getBoolean("independent")) throw new SecurityException("Helper lifetime was not confirmed");
                    attachedIndependent = true;
                    closeAfter = (boot || !automatic) && policy.getBoolean("brief_debug_at_boot", false);
                    boolean matched = state.getBoolean("matched");
                    if (matched) {
                        if (received == null || !identity.edit().putString("connect_server_pin", received)
                                .putInt("pin_boot", bootCount).commit())
                            throw new IllegalStateException("Helper attached but current-daemon pin was not saved");
                    }
                    return finish("Settings helper is connected", bootCount, boot, started, matched);
                }
                Thread.sleep(350);
            }
            throw new SecurityException("The signed launcher did not confirm helper attachment");
        } catch (Exception failure) {
            finish("Startup stopped: " + failure.getClass().getSimpleName(), bootCount, boot, started, false);
            throw failure;
        } finally {
            try {
                if (openedDebug || (closeAfter && attachedIndependent)) {
                    if (!Settings.Global.putInt(context.getContentResolver(), "adb_wifi_enabled", 0))
                        throw new SecurityException("Could not turn Wireless debugging off");
                    Thread.sleep(350);
                    Bundle state = new SignedLauncher(context).call("status", null);
                    boolean kept = state.getBoolean("connected") && state.getBoolean("independent")
                            && Settings.Global.getInt(context.getContentResolver(), "adb_wifi_enabled", -1) == 0;
                    SharedPreferences.Editor closed = policy.edit().putBoolean("last_debug_closed", kept)
                            .putString("last_status", kept ? "Helper connected - Wireless debugging off"
                                    : "Wireless debugging off; helper status needs checking");
                    if (boot) closed.putBoolean("boot_debug_closed", kept);
                    closed.commit();
                }
            } finally { BUSY.set(false); }
        }
    }
    private String finish(String message, int bootCount, boolean boot, long started, boolean proof) {
        SharedPreferences.Editor record = context.getSharedPreferences("startup", 0).edit().putString("last_status", message)
                .putInt("last_boot", bootCount).putBoolean("last_was_boot", boot)
                .putBoolean("last_matched", proof).putLong("last_elapsed_ms", SystemClock.elapsedRealtime() - started)
                .putLong("last_wall_time", System.currentTimeMillis());
        if (boot) record.putInt("boot_result_count", bootCount).putString("boot_result", message)
                .putBoolean("boot_nonce_matched", proof).putLong("boot_elapsed_ms", SystemClock.elapsedRealtime() - started);
        record.commit();
        return message;
    }
}
