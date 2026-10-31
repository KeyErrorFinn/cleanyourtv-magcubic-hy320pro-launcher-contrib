package local.hy320.helperstarter;
/** JNI interface to the Apache-2.0 Shizuku pairing implementation; not a new crypto scheme. */
final class PairingContext implements AutoCloseable {
 static {System.loadLibrary("localpairing");}
 private long pointer;
 PairingContext(boolean client,byte[] password){pointer=nativeConstructor(client,password);if(pointer==0)throw new IllegalStateException("Pairing context unavailable");}
 byte[] message(){return nativeMsg(pointer);}boolean init(byte[] peer){return nativeInitCipher(pointer,peer);}
 byte[] encrypt(byte[] data){return nativeEncrypt(pointer,data);}byte[] decrypt(byte[] data){return nativeDecrypt(pointer,data);}
 public void close(){if(pointer!=0){nativeDestroy(pointer);pointer=0;}}
 private static native long nativeConstructor(boolean client,byte[] password);
 private native byte[] nativeMsg(long p);private native boolean nativeInitCipher(long p,byte[] data);private native byte[] nativeEncrypt(long p,byte[] data);private native byte[] nativeDecrypt(long p,byte[] data);private native void nativeDestroy(long p);
 static void selfTest(){byte[] secret="local-self-test-only".getBytes(java.nio.charset.StandardCharsets.UTF_8);try(PairingContext a=new PairingContext(true,secret);PairingContext b=new PairingContext(false,secret)){if(!a.init(b.message())||!b.init(a.message()))throw new IllegalStateException("SPAKE self-check failed");byte[] plain="roundtrip".getBytes(java.nio.charset.StandardCharsets.UTF_8);if(!java.util.Arrays.equals(plain,b.decrypt(a.encrypt(plain)))||!java.util.Arrays.equals(plain,a.decrypt(b.encrypt(plain))))throw new IllegalStateException("AEAD self-check failed");}java.util.Arrays.fill(secret,(byte)0);}
}
