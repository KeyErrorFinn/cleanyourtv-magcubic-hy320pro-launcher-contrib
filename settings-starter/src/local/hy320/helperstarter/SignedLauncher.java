package local.hy320.helperstarter;
import android.content.*;import android.content.pm.*;import android.net.Uri;import android.os.*;import java.security.*;
/** Reads only the signed launcher's typed startup/status provider. */
final class SignedLauncher {
 static final String PACKAGE="com.gathin.tvhome",CERT=ReleaseIdentity.CERT;
 private final Context context;private final String path;
 SignedLauncher(Context context)throws Exception{this.context=context;if(Build.VERSION.SDK_INT!=34||!"Projector_20260713.153921".equals(Build.ID)||android.os.Process.myUid()/100000!=0)throw new SecurityException("Reviewed projector and user required");PackageInfo app=context.getPackageManager().getPackageInfo(PACKAGE,PackageManager.GET_SIGNING_CERTIFICATES);android.content.pm.Signature[] signatures=app.signingInfo.getApkContentsSigners();if(signatures.length!=1)throw new SecurityException("Unexpected launcher signing identity");StringBuilder digest=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(signatures[0].toByteArray()))digest.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!CERT.contentEquals(digest))throw new SecurityException("Launcher signing identity changed");path=app.applicationInfo.sourceDir;}
 Bundle call(String method,String nonce){Bundle input=new Bundle();if(nonce!=null)input.putString("nonce",nonce);if("request-start".equals(method))input.putBoolean("independent",true);Bundle result=context.getContentResolver().call(Uri.parse("content://com.gathin.tvhome.starter-status"),method,null,input);if(result==null)throw new IllegalStateException("Signed launcher status unavailable");if(result.getBoolean("connected")&&result.getInt("uid",-1)!=2000)throw new SecurityException("Helper shell identity unconfirmed");return result;}
 String command(String nonce){return BootstrapContract.command(path,nonce);}
}
