package io.nekohasekai.sagernet.fmt.awg;

import androidx.annotation.NonNull;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.ByteBufferOutput;

import org.jetbrains.annotations.NotNull;

import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

/**
 * Профиль AmneziaWG 3.1.
 *
 * Отдельный тип, а не расширение WireGuardBean: у AWG нет поля reserved,
 * зато есть собственный набор параметров обфускации. Держать это в одном
 * бине означало бы тащить в обычный WireGuard двадцать лишних полей,
 * из которых он всё равно не использует ни одного.
 */
public class AwgBean extends AbstractBean {

    public String localAddress;
    public String privateKey;
    public String peerPublicKey;
    public String peerPreSharedKey;
    public Integer mtu;
    public Integer persistentKeepaliveInterval;

    // Мусорные пакеты перед каждым хендшейком.
    public Integer junkCount;
    public Integer junkMin;
    public Integer junkMax;

    // Криптографические паддинги сообщений.
    public Integer initPadding;
    public Integer responsePadding;
    public Integer cookiePadding;
    public Integer transportPadding;

    // Заголовки: одно число либо диапазон «x-y».
    public String initHeader;
    public String responseHeader;
    public String cookieHeader;
    public String transportHeader;

    // Подписи из тегов <b 0x..>, <r N>, <rd N>, <rc N>, <t>.
    public String signature1;
    public String signature2;
    public String signature3;
    public String signature4;
    public String signature5;

    public String headerProtectionKey;
    public String contentPaddingAddition;

    public Boolean randomTrailers;
    public Boolean disableCookies;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();
        if (localAddress == null) localAddress = "";
        if (privateKey == null) privateKey = "";
        if (peerPublicKey == null) peerPublicKey = "";
        if (peerPreSharedKey == null) peerPreSharedKey = "";
        if (mtu == null) mtu = 1420;
        if (persistentKeepaliveInterval == null) persistentKeepaliveInterval = 0;
        if (junkCount == null) junkCount = 0;
        if (junkMin == null) junkMin = 0;
        if (junkMax == null) junkMax = 0;
        if (initPadding == null) initPadding = 0;
        if (responsePadding == null) responsePadding = 0;
        if (cookiePadding == null) cookiePadding = 0;
        if (transportPadding == null) transportPadding = 0;
        if (initHeader == null) initHeader = "";
        if (responseHeader == null) responseHeader = "";
        if (cookieHeader == null) cookieHeader = "";
        if (transportHeader == null) transportHeader = "";
        if (signature1 == null) signature1 = "";
        if (signature2 == null) signature2 = "";
        if (signature3 == null) signature3 = "";
        if (signature4 == null) signature4 = "";
        if (signature5 == null) signature5 = "";
        if (headerProtectionKey == null) headerProtectionKey = "";
        if (contentPaddingAddition == null) contentPaddingAddition = "";
        if (randomTrailers == null) randomTrailers = false;
        if (disableCookies == null) disableCookies = false;
    }

    @Override
    public void serialize(ByteBufferOutput output) {
        output.writeInt(1);
        super.serialize(output);
        output.writeString(localAddress);
        output.writeString(privateKey);
        output.writeString(peerPublicKey);
        output.writeString(peerPreSharedKey);
        output.writeInt(mtu);
        output.writeInt(persistentKeepaliveInterval);
        output.writeInt(junkCount);
        output.writeInt(junkMin);
        output.writeInt(junkMax);
        output.writeInt(initPadding);
        output.writeInt(responsePadding);
        output.writeInt(cookiePadding);
        output.writeInt(transportPadding);
        output.writeString(initHeader);
        output.writeString(responseHeader);
        output.writeString(cookieHeader);
        output.writeString(transportHeader);
        output.writeString(signature1);
        output.writeString(signature2);
        output.writeString(signature3);
        output.writeString(signature4);
        output.writeString(signature5);
        output.writeString(headerProtectionKey);
        output.writeString(contentPaddingAddition);
        output.writeBoolean(randomTrailers);
        output.writeBoolean(disableCookies);
    }

    @Override
    public void deserialize(ByteBufferInput input) {
        int version = input.readInt();
        super.deserialize(input);
        localAddress = input.readString();
        privateKey = input.readString();
        peerPublicKey = input.readString();
        peerPreSharedKey = input.readString();
        mtu = input.readInt();
        persistentKeepaliveInterval = input.readInt();
        junkCount = input.readInt();
        junkMin = input.readInt();
        junkMax = input.readInt();
        initPadding = input.readInt();
        responsePadding = input.readInt();
        cookiePadding = input.readInt();
        transportPadding = input.readInt();
        initHeader = input.readString();
        responseHeader = input.readString();
        cookieHeader = input.readString();
        transportHeader = input.readString();
        signature1 = input.readString();
        signature2 = input.readString();
        signature3 = input.readString();
        signature4 = input.readString();
        signature5 = input.readString();
        headerProtectionKey = input.readString();
        contentPaddingAddition = input.readString();
        randomTrailers = input.readBoolean();
        disableCookies = input.readBoolean();
    }

    @Override
    public boolean canTCPing() {
        return false;
    }

    @NotNull
    @Override
    public AwgBean clone() {
        return KryoConverters.deserialize(new AwgBean(), KryoConverters.serialize(this));
    }

    public static final Creator<AwgBean> CREATOR = new CREATOR<AwgBean>() {
        @NonNull
        @Override
        public AwgBean newInstance() {
            return new AwgBean();
        }

        @Override
        public AwgBean[] newArray(int size) {
            return new AwgBean[size];
        }
    };
}