package local.hy320.helperstarter;
import android.content.*;
/** Signature-protected recovery when the launcher process is recreated; never re-enables debugging. */
public final class ReadyReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){if("com.gathin.tvhome.SETTINGS_STARTER_STOPPED".equals(intent.getAction())){context.getSharedPreferences("startup",0).edit().putInt("suspended_boot",android.provider.Settings.Global.getInt(context.getContentResolver(),"boot_count",-1)).apply();context.getSystemService(android.app.job.JobScheduler.class).cancel(StarterJob.ID);}else if("com.gathin.tvhome.SETTINGS_STARTER_READY".equals(intent.getAction())){android.content.SharedPreferences p=context.getSharedPreferences("startup",0);int boot=android.provider.Settings.Global.getInt(context.getContentResolver(),"boot_count",-1);if(BootstrapContract.mayRecover(boot,p.getInt("seen_boot",-2),p.getInt("last_boot",-2)))StarterJob.schedule(context,false);}}
}
