package io.nekohasekai.sagernet.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.preference.EditTextPreferenceModifiers
import io.nekohasekai.sagernet.fmt.awg.AwgBean
import moe.matsuri.nb4a.proxy.PreferenceBinding
import moe.matsuri.nb4a.proxy.PreferenceBindingManager
import moe.matsuri.nb4a.proxy.Type

class AwgSettingsActivity : ProfileSettingsActivity<AwgBean>() {

    override fun createEntity() = AwgBean()

    private val pbm = PreferenceBindingManager()

    // Подключение
    private val serverAddress = pbm.add(PreferenceBinding(Type.Text, "serverAddress"))
    private val serverPort = pbm.add(PreferenceBinding(Type.TextToInt, "serverPort"))
    private val localAddress = pbm.add(PreferenceBinding(Type.Text, "localAddress"))
    private val privateKey = pbm.add(PreferenceBinding(Type.Text, "privateKey"))
    private val peerPublicKey = pbm.add(PreferenceBinding(Type.Text, "peerPublicKey"))
    private val peerPreSharedKey = pbm.add(PreferenceBinding(Type.Text, "peerPreSharedKey"))
    private val mtu = pbm.add(PreferenceBinding(Type.TextToInt, "mtu"))
    private val persistentKeepaliveInterval =
        pbm.add(PreferenceBinding(Type.TextToInt, "persistentKeepaliveInterval"))

    // Мусорные пакеты
    private val junkCount = pbm.add(PreferenceBinding(Type.TextToInt, "junkCount"))
    private val junkMin = pbm.add(PreferenceBinding(Type.TextToInt, "junkMin"))
    private val junkMax = pbm.add(PreferenceBinding(Type.TextToInt, "junkMax"))

    // Паддинги
    private val initPadding = pbm.add(PreferenceBinding(Type.TextToInt, "initPadding"))
    private val responsePadding = pbm.add(PreferenceBinding(Type.TextToInt, "responsePadding"))
    private val cookiePadding = pbm.add(PreferenceBinding(Type.TextToInt, "cookiePadding"))
    private val transportPadding = pbm.add(PreferenceBinding(Type.TextToInt, "transportPadding"))

    // Заголовки
    private val initHeader = pbm.add(PreferenceBinding(Type.Text, "initHeader"))
    private val responseHeader = pbm.add(PreferenceBinding(Type.Text, "responseHeader"))
    private val cookieHeader = pbm.add(PreferenceBinding(Type.Text, "cookieHeader"))
    private val transportHeader = pbm.add(PreferenceBinding(Type.Text, "transportHeader"))

    // Подписи
    private val signature1 = pbm.add(PreferenceBinding(Type.Text, "signature1"))
    private val signature2 = pbm.add(PreferenceBinding(Type.Text, "signature2"))
    private val signature3 = pbm.add(PreferenceBinding(Type.Text, "signature3"))
    private val signature4 = pbm.add(PreferenceBinding(Type.Text, "signature4"))
    private val signature5 = pbm.add(PreferenceBinding(Type.Text, "signature5"))

    // Прочее
    private val headerProtectionKey = pbm.add(PreferenceBinding(Type.Text, "headerProtectionKey"))
    private val contentPaddingAddition =
        pbm.add(PreferenceBinding(Type.Text, "contentPaddingAddition"))
    private val randomTrailers = pbm.add(PreferenceBinding(Type.Bool, "randomTrailers"))
    private val disableCookies = pbm.add(PreferenceBinding(Type.Bool, "disableCookies"))

    override fun AwgBean.init() {
        pbm.writeToCacheAll(this)
    }

    override fun AwgBean.serialize() {
        pbm.fromCacheAll(this)
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.awg_preferences)
        pbm.setPreferenceFragment(this)

        (serverPort.preference as EditTextPreference)
            .setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        (privateKey.preference as EditTextPreference).summaryProvider = PasswordSummaryProvider
        (headerProtectionKey.preference as EditTextPreference).summaryProvider = PasswordSummaryProvider
        (mtu.preference as EditTextPreference).setOnBindEditTextListener(EditTextPreferenceModifiers.Number)

        // Числовые поля обфускации: пустое значение — это ноль, а не ошибка.
        // Ноль в ядре означает «параметр не задан», поэтому требовать цифру
        // в каждом поле незачем: оставляем как есть.
        listOf(
            persistentKeepaliveInterval, junkCount, junkMin, junkMax,
            initPadding, responsePadding, cookiePadding, transportPadding,
        ).forEach {
            (it.preference as EditTextPreference)
                .setOnBindEditTextListener(EditTextPreferenceModifiers.Number)
        }
    }

}