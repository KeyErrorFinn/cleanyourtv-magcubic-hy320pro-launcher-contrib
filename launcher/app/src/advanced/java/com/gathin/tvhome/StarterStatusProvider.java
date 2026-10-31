package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

/** Signed companion status only; exposes no settings commands, helper Binder or shell terminal. */
public final class StarterStatusProvider extends ContentProvider {
    static final StarterHandshake HANDSHAKE = new StarterHandshake();
    private static final String STARTER = "local.hy320.helperstarter";

    @Override public boolean onCreate() { return true; }

    private void caller() {
        if (!"Projector_20260713.153921".equals(Build.ID) || Build.VERSION.SDK_INT != 34)
            throw new SecurityException("Reviewed projector required");
        try {
            PackageManager pm = getContext().getPackageManager();
            PackageInfo owner = pm.getPackageInfo(STARTER, 0);
            if (owner.applicationInfo.uid != Binder.getCallingUid()
                    || pm.checkSignatures(getContext().getPackageName(), STARTER)
                    != PackageManager.SIGNATURE_MATCH)
                throw new SecurityException("Signed settings starter required");
        } catch (PackageManager.NameNotFoundException missing) {
            throw new SecurityException("Settings starter is not installed", missing);
        }
    }

    @Override public Bundle call(String method, String arg, Bundle input) {
        caller();
        long identity = Binder.clearCallingIdentity();
        try {
            Bundle result = new Bundle();
            boolean connected = SettingsSessionClient.connected();
            if (connected) {
                Bundle status = SettingsSessionClient.call(SettingsSessionClient.STATUS, null);
                if (status.getInt("uid", -1) != 2000)
                    throw new SecurityException("Helper identity could not be confirmed");
                result.putInt("uid", 2000);
            }
            result.putBoolean("connected", connected);
            result.putBoolean("independent", SettingsSessionClient.independentOfDebugging());
            if ("request-start".equals(method)) {
                if (!connected) {
                    byte[] bytes = new byte[16];
                    new java.security.SecureRandom().nextBytes(bytes);
                    StringBuilder token = new StringBuilder();
                    for (byte b : bytes)
                        token.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                    result.putString("nonce", HANDSHAKE.begin(token.toString(),
                            SystemClock.elapsedRealtime(), input != null && input.getBoolean("independent")));
                }
            } else if ("status".equals(method)) {
                result.putBoolean("matched", connected && input != null
                        && HANDSHAKE.matches(input.getString("nonce")));
            } else {
                throw new IllegalArgumentException("Unsupported starter request");
            }
            return result;
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Helper status is unavailable", failure);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        throw new UnsupportedOperationException();
    }
    @Override public String getType(Uri u) { return "vnd.android.cursor.item/vnd.cleanyourtv.starter"; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) {
        throw new UnsupportedOperationException();
    }
}
