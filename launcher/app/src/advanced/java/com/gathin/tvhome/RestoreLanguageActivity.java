package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Explicit locale-only restore entry; caller must already hold CHANGE_CONFIGURATION. */
public final class RestoreLanguageActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        TextView message=new TextView(this);message.setText("Restoring system language…");
        message.setTextColor(0xfff5f5fa);message.setBackgroundColor(0xff101216);message.setTextSize(24);
        message.setGravity(android.view.Gravity.CENTER);message.setTypeface(UiFont.get(this,R.string.font_regular));setContentView(message);
        String tags=getIntent().getStringExtra("com.gathin.tvhome.RESTORE_LANGUAGES");
        if(!"Projector_20260713.153921".equals(android.os.Build.ID)||android.os.Build.VERSION.SDK_INT!=34||tags==null){finish();return;}
        new Thread(()->{
            String result;
            try {
                SystemLanguageControl control=new SystemLanguageControl(this);
                control.set(control.read(),tags);
                result="verified";
            } catch(Exception failure){result="failed";}
            android.util.Log.i("CYTVLocaleRestore","restore="+result);
            final String status=result;
            runOnUiThread(()->{
                if(!isDestroyed()) {
                    if(!"verified".equals(status))android.widget.Toast.makeText(this,"Language restore was not confirmed. Original values remain in the backup.",android.widget.Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        },"restore-system-language").start();
    }
}
