package local.hy320.helperstarter;
/** Pure validation and boot policy; no Android authority and no generic shell inputs. */
public final class BootstrapContract {
 public static boolean validNonce(String value){return value!=null&&value.matches("[0-9a-f]{32}");}
 public static String command(String path,String nonce){if(path==null||!path.matches("/data/app/[A-Za-z0-9_./=+~-]+/base\\.apk")||path.contains("..")||!validNonce(nonce))throw new IllegalArgumentException("Untrusted helper launch path or token");return "CLASSPATH='"+path+"' nohup app_process /system/bin --nice-name=cleanyourtv-settings-helper com.gathin.tvhome.ShellSettingsSession --starter-nonce "+nonce+" > /data/local/tmp/cleanyourtv-settings-helper.log 2>&1 < /dev/null &";}
 public static boolean mayEnableDebugging(boolean boot,boolean enabled,boolean allow,int currentBoot,int lastEnableBoot,boolean developerOptions){return boot&&enabled&&allow&&developerOptions&&currentBoot>=0&&currentBoot!=lastEnableBoot;}
 public static boolean needsFreshDaemonProof(int pinBoot,int currentBoot,boolean realBoot,boolean enabled){if(currentBoot<0)throw new SecurityException("Boot identity unavailable");if(pinBoot==currentBoot)return false;if(!realBoot||!enabled)throw new SecurityException("A new ADB daemon needs authorised boot startup or computer setup");return true;}
 public static boolean mayRecover(int boot,int seen,int completed){return boot>=0&&seen==boot&&completed==boot;}
 private BootstrapContract(){}
}
