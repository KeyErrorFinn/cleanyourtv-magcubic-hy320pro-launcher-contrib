package local.hy320.helperstarter;
import android.content.*;
public final class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){context.getSharedPreferences("startup",0).edit().putInt("seen_boot",android.provider.Settings.Global.getInt(context.getContentResolver(),"boot_count",-1)).apply();StarterJob.schedule(context,true);}else if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))StarterJob.schedule(context,false);}
}
