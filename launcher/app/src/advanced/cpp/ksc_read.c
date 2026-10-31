/* Exact getter used by this firmware's libksc_get_para. No setters or caller-selected ioctl. */
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <sys/ioctl.h>
#include <unistd.h>

_Static_assert(sizeof(void *) == 4, "Reviewed ARM32 OEM getter only");
JNIEXPORT jintArray JNICALL Java_com_gathin_tvhome_DriverKeystoneState_nativeRead(JNIEnv *env, jclass type) {
    (void) type;
    uint32_t data[85] = {0};
    uintptr_t args[4] = {(uintptr_t)data, 0, 0, 0};
    jint receipt[10] = {-1, 0};
    int fd = open("/dev/ksc", O_RDWR | O_CLOEXEC);
    if (fd < 0) receipt[1] = errno;
    else {
        receipt[0] = ioctl(fd, 0x41546b01UL, args);
        if (receipt[0] != 0) receipt[1] = errno;
        close(fd);
    }
    if (receipt[0] == 0) for (int i = 0; i < 8; i++) receipt[i + 2] = (jint)data[i + 6];
    jintArray result = (*env)->NewIntArray(env, 10);
    if (result != NULL) (*env)->SetIntArrayRegion(env, result, 0, 10, receipt);
    return result;
}
