#include <jni.h>
#include <stdio.h>
#include <time.h>
#include <unistd.h>
#include <android/log.h>
#include <arpa/inet.h>
#include <netinet/ip.h>
#include <netinet/ip6.h>
#include <netinet/udp.h>
#include <netinet/tcp.h>

#define TAG "NetGuard.JNI"
#define MAXPKT 32768

void decode(JNIEnv *env, jobject instance, jbyte *, int);

int getUid(int, int, void *, int);

JNIEXPORT void JNICALL
Java_eu_faircode_netguard_SinkholeService_jni_1init(JNIEnv *env, jobject instance) {
    __android_log_print(ANDROID_LOG_INFO, TAG, "Init", 1);
}

JNIEXPORT void JNICALL
Java_eu_faircode_netguard_SinkholeService_jni_1decode(JNIEnv *env, jobject instance,
                                                      jbyteArray buffer_, jint length) {
    jbyte *buffer = (*env)->GetByteArrayElements(env, buffer_, NULL);
    decode(env, instance, buffer, length);
    (*env)->ReleaseByteArrayElements(env, buffer_, buffer, 0);
}

JNIEXPORT void JNICALL
Java_eu_faircode_netguard_SinkholeService_jni_1receive(JNIEnv *env, jobject instance, jint fd) {
    int len;
    jbyte buffer[MAXPKT];
    while (1) {
        len = read(fd, buffer, sizeof(buffer));
        if (len < 0) {
            __android_log_print(ANDROID_LOG_WARN, TAG, "Receive error %d", len);
            return;

        } else if (len > 0)
            decode(env, instance, buffer, len);

        else
            __android_log_print(ANDROID_LOG_WARN, TAG, "Nothing received");
    }
}

void decode(JNIEnv *env, jobject instance, jbyte *buffer, int length) {
    jbyte protocol;
    void *saddr;
    void *daddr;
    char source[40];
    char dest[40];
    char flags[10];
    int flen = 0;
    jbyte *payload;

    // Get protocol, addresses & payload
    jbyte version = (*buffer) >> 4;
    if (version == 4) {
        struct iphdr *ip4hdr = buffer;

        protocol = ip4hdr->protocol;
        saddr = &ip4hdr->saddr;
        daddr = &ip4hdr->daddr;

        if (ip4hdr->frag_off & IP_MF)
            flags[flen++] = '+';

        jbyte optlen = 0;
        if (ip4hdr->ihl > 5)
            optlen = buffer[20];
        payload = buffer + (20 + optlen) * sizeof(jbyte);
    }
    else if (version == 6) {
        struct ip6_hdr *ip6hdr = buffer;

        protocol = ip6hdr->ip6_nxt;
        saddr = &ip6hdr->ip6_src;
        daddr = &ip6hdr->ip6_dst;

        payload = buffer + 40 * sizeof(jbyte);
    }
    else {
        __android_log_print(ANDROID_LOG_WARN, TAG, "Unknown version %d", version);
        return;
    }

    // Get ports & flags
    int sport = -1;
    int dport = -1;
    if (protocol == IPPROTO_TCP) {
        struct tcphdr *tcp = payload;

        sport = ntohs(tcp->source);
        dport = ntohs(tcp->dest);

        if (tcp->syn)
            flags[flen++] = 'S';
        if (tcp->ack)
            flags[flen++] = 'A';
        if (tcp->psh)
            flags[flen++] = 'P';
        if (tcp->fin)
            flags[flen++] = 'F';
        if (tcp->fin)
            flags[flen++] = 'R';
    } else if (protocol == IPPROTO_UDP) {
        struct udphdr *udp = payload;

        sport = ntohs(udp->source);
        dport = ntohs(udp->dest);
    }

    flags[flen] = 0;

    inet_ntop(version == 4 ? AF_INET : AF_INET6, saddr, source, sizeof(source));
    inet_ntop(version == 4 ? AF_INET : AF_INET6, daddr, dest, sizeof(dest));
    // __android_log_print(ANDROID_LOG_INFO, TAG, "Packet v%d %s/%d -> %s/%d proto %d flags %s",
    //                     version, source, sport, dest, dport, protocol, flags);

    // Get uid
    int uid = -1;
    if (protocol == IPPROTO_TCP || protocol == IPPROTO_UDP) {
        // Sleep 10 ms
        struct timespec tim, tim2;
        tim.tv_sec = 0;
        tim.tv_nsec = 10000000L;
        nanosleep(&tim, &tim2);

        // Lookup uid
        uid = getUid(protocol, version, saddr, sport);
        if (uid < 0 && version == 4) {
            int8_t saddr128[16];
            memset(saddr128, 0, 10);
            saddr128[10] = 0xFF;
            saddr128[11] = 0xFF;
            memcpy(saddr128 + 12, saddr, 4);
            uid = getUid(protocol, 6, saddr128, sport);
        }
    }

    // Call back
    jclass cls = (*env)->GetObjectClass(env, instance);
    jmethodID mid = (*env)->GetMethodID(env, cls, "logPacket",
                                        "(ILjava/lang/String;ILjava/lang/String;IILjava/lang/String;I)V");
    if (mid != 0) {
        jstring jsource = (*env)->NewStringUTF(env, source);
        jstring jdest = (*env)->NewStringUTF(env, dest);
        jstring jflags = (*env)->NewStringUTF(env, flags);
        (*env)->CallVoidMethod(env, instance, mid,
                               version, jsource, sport, jdest, dport, protocol, jflags, uid);
        (*env)->DeleteLocalRef(env, jsource);
        (*env)->DeleteLocalRef(env, jdest);
        (*env)->DeleteLocalRef(env, jflags);

        jthrowable ex = (*env)->ExceptionOccurred(env);
        if (ex) {
            (*env)->ExceptionDescribe(env);
            (*env)->ExceptionClear(env);
            (*env)->DeleteLocalRef(env, ex);
        }
    }
}

int getUid(int protocol, int version, void *saddr, int sport) {
    char line[250];
    int fields;
    int32_t addr32;
    int8_t addr128[16];
    int port;
    int uid = -1;

    // Get proc file name
    char *fn = NULL;
    if (protocol == IPPROTO_TCP)
        fn = (version == 4 ? "/proc/net/tcp" : "/proc/net/tcp6");
    else if (protocol == IPPROTO_UDP)
        fn = (version == 4 ? "/proc/net/udp" : "/proc/net/udp6");
    else
        return -1;

    // Open proc file
    FILE *fd = fopen(fn, "r");
    if (fd == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Error opening %s", fn);
        return -1;
    }

    // Scan proc file
    int i = 0;
    while (fgets(line, sizeof(line), fd) != NULL) {
        if (i++) {
            if (version == 4)
                fields = sscanf(line,
                                "%*d: %X:%X %*X:%*X %*X %*lX:%*lX %*X:%*X %*X %d %*d %*ld ",
                                &addr32, &port, &uid);
            else
                fields = sscanf(line,
                                "%*d: %8X%8X%8X%8X:%X %*X:%*X %*X %*lX:%*lX %*X:%*X %*X %d %*d %*ld ",
                                addr128, addr128 + 4, addr128 + 8, addr128 + 12, &port, &uid);

            if (fields < 3) {
                __android_log_print(ANDROID_LOG_ERROR, TAG, "Invalid field #%d: %s", fields, line);
                break;
            }

            if (port == sport) {
                if (version == 4) {
                    if (addr32 == *((int32_t *) saddr))
                        return uid;
                }
                else {
                    if (memcmp(addr128, saddr, (size_t) 16) == 0)
                        return uid;
                }
            }
        }
    }

    fclose(fd);

    return -1;
}