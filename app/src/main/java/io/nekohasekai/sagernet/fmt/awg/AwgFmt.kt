package io.nekohasekai.sagernet.fmt.awg

import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma
import org.ini4j.Ini
import org.ini4j.Section
import java.io.StringReader

/**
 * Сборка исходящего AmneziaWG.
 *
 * Пустые строки и нули в конфигурацию не попадают: в amneziawg-go
 * отсутствие параметра и ноль — одно и то же, а явное `h1=` не
 * распарсилось бы как диапазон и уронило бы весь хендшейк.
 */
fun buildSingBoxOutboundAwgBean(bean: AwgBean): SingBoxOptions.Outbound_AWGOptions {
    return SingBoxOptions.Outbound_AWGOptions().apply {
        type = "awg"
        server = bean.serverAddress
        server_port = bean.serverPort
        local_address = bean.localAddress.listByLineOrComma()
        private_key = bean.privateKey
        peer_public_key = bean.peerPublicKey
        if (bean.peerPreSharedKey.isNotBlank()) pre_shared_key = bean.peerPreSharedKey
        if (bean.mtu > 0) mtu = bean.mtu
        if (bean.persistentKeepaliveInterval > 0) {
            persistent_keepalive_interval = bean.persistentKeepaliveInterval
        }

        // Мусорные пакеты. Нулевое значение в ядре равносильно «не
        // отправлять», поэтому при нуле поле опускается целиком.
        if (bean.junkCount != 0) junk_count = bean.junkCount
        if (bean.junkMin != 0) junk_min = bean.junkMin
        if (bean.junkMax != 0) junk_max = bean.junkMax

        if (bean.initPadding != 0) init_padding = bean.initPadding
        if (bean.responsePadding != 0) response_padding = bean.responsePadding
        if (bean.cookiePadding != 0) cookie_padding = bean.cookiePadding
        if (bean.transportPadding != 0) transport_padding = bean.transportPadding

        if (bean.initHeader.isNotBlank()) init_header = bean.initHeader
        if (bean.responseHeader.isNotBlank()) response_header = bean.responseHeader
        if (bean.cookieHeader.isNotBlank()) cookie_header = bean.cookieHeader
        if (bean.transportHeader.isNotBlank()) transport_header = bean.transportHeader

        if (bean.signature1.isNotBlank()) signature_1 = bean.signature1
        if (bean.signature2.isNotBlank()) signature_2 = bean.signature2
        if (bean.signature3.isNotBlank()) signature_3 = bean.signature3
        if (bean.signature4.isNotBlank()) signature_4 = bean.signature4
        if (bean.signature5.isNotBlank()) signature_5 = bean.signature5

        if (bean.headerProtectionKey.isNotBlank()) header_protection_key = bean.headerProtectionKey
        if (bean.contentPaddingAddition.isNotBlank()) {
            content_padding_addition = bean.contentPaddingAddition
        }

        if (bean.randomTrailers) random_trailers = true
        if (bean.disableCookies) disable_cookies = true
    }
}

/**
 * Ключи, по которым конфигурация опознаётся как AmneziaWG, а не как
 * обычный WireGuard. Взяты из README amneziawg-go, раздел Configuration.
 */
private val AWG_MARKER_KEYS = listOf(
    "Jc", "Jmin", "Jmax",
    "S1", "S2", "S3", "S4",
    "H1", "H2", "H3", "H4",
    "I1", "I2", "I3", "I4", "I5",
    "HeaderProtectionKey", "ContentPaddingAddition",
    "RandomTrailers", "DisableCookies",
)

/**
 * Ищет ключ без учёта регистра.
 *
 * ini4j вроде бы регистронезависим, но в выгрузках встречается и `Jc`,
 * и `jc`, а полагаться на это в импортере конфигураций нельзя:
 * расхождение выглядело бы как «пропали настройки обфускации».
 */
private fun Section.value(key: String): String? {
    val direct = get(key)
    if (direct != null && direct.isNotBlank()) return direct
    for (k in keySet()) {
        if (!k.equals(key, ignoreCase = true)) continue
        val found = get(k)
        if (found != null && found.isNotBlank()) return found
    }
    return null
}

private fun Section.boolValue(key: String): Boolean {
    val raw = value(key) ?: return false
    return raw.equals("true", ignoreCase = true) || raw == "1"
}

/** Есть ли в секции [Interface] хоть один ключ, которого нет в обычном WireGuard. */
fun isAwgIni(ini: Ini): Boolean {
    val iface = ini["Interface"] ?: return false
    return AWG_MARKER_KEYS.any { iface.value(it) != null }
}

/**
 * Разбор конфигурации AmneziaWG в формате .conf, как их пишет Amnezia.
 *
 * Возвращает по профилю на каждого пира: в одном файле их может быть
 * несколько, и у них разные адреса, ключи и keepalive.
 */
fun parseAwgConf(conf: String): List<AwgBean> {
    val ini = Ini(StringReader(conf))
    val iface = ini["Interface"] ?: error("Missing 'Interface' selection")

    val addressValues = iface.getAll("Address")
    if (addressValues.isNullOrEmpty()) error("Empty address in 'Interface' selection")
    val localAddress = addressValues
        .flatMap { it.split(",") }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (localAddress.isEmpty()) error("Empty address in 'Interface' selection")

    val peers = ini.getAll("Peer")
    if (peers.isNullOrEmpty()) error("Missing 'Peer' selections")

    // Всё, что относится к устройству, читаем один раз: секций [Peer]
    // может быть много, а эти настройки у них общие.
    val privateKey = iface.value("PrivateKey").orEmpty()
    val mtu = iface.value("MTU")?.toIntOrNull()

    val junkCount = iface.value("Jc")?.toIntOrNull() ?: 0
    val junkMin = iface.value("Jmin")?.toIntOrNull() ?: 0
    val junkMax = iface.value("Jmax")?.toIntOrNull() ?: 0

    val initPadding = iface.value("S1")?.toIntOrNull() ?: 0
    val responsePadding = iface.value("S2")?.toIntOrNull() ?: 0
    val cookiePadding = iface.value("S3")?.toIntOrNull() ?: 0
    val transportPadding = iface.value("S4")?.toIntOrNull() ?: 0

    val initHeader = iface.value("H1").orEmpty()
    val responseHeader = iface.value("H2").orEmpty()
    val cookieHeader = iface.value("H3").orEmpty()
    val transportHeader = iface.value("H4").orEmpty()

    val signature1 = iface.value("I1").orEmpty()
    val signature2 = iface.value("I2").orEmpty()
    val signature3 = iface.value("I3").orEmpty()
    val signature4 = iface.value("I4").orEmpty()
    val signature5 = iface.value("I5").orEmpty()

    val headerProtectionKey = iface.value("HeaderProtectionKey").orEmpty()
    val contentPaddingAddition = iface.value("ContentPaddingAddition").orEmpty()
    val randomTrailers = iface.boolValue("RandomTrailers")
    val disableCookies = iface.boolValue("DisableCookies")

    val beans = mutableListOf<AwgBean>()
    for (peer in peers) {
        val endpoint = peer.value("Endpoint")
        if (endpoint.isNullOrBlank() || !endpoint.contains(":")) continue

        // PersistentKeepalive в AWG допускает диапазон «25-30». Бин держит
        // целое, поэтому берём левую границу. Молча выбрасывать нельзя:
        // без keepalive NAT-соединение будет рваться по таймауту.
        val keepalive = peer.value("PersistentKeepalive")
            ?.substringBefore('-')?.trim()?.toIntOrNull() ?: 0

        val peerPort = endpoint.substringAfterLast(":").toIntOrNull() ?: continue
        val peerKey = peer.value("PublicKey") ?: continue

        val bean = AwgBean()
        bean.serverAddress = endpoint.substringBeforeLast(":")
        bean.serverPort = peerPort
        bean.peerPublicKey = peerKey
        bean.peerPreSharedKey = peer.value("PresharedKey").orEmpty()
        bean.localAddress = localAddress.joinToString("\n")
        bean.privateKey = privateKey
        if (mtu != null) bean.mtu = mtu
        bean.persistentKeepaliveInterval = keepalive
        bean.junkCount = junkCount
        bean.junkMin = junkMin
        bean.junkMax = junkMax
        bean.initPadding = initPadding
        bean.responsePadding = responsePadding
        bean.cookiePadding = cookiePadding
        bean.transportPadding = transportPadding
        bean.initHeader = initHeader
        bean.responseHeader = responseHeader
        bean.cookieHeader = cookieHeader
        bean.transportHeader = transportHeader
        bean.signature1 = signature1
        bean.signature2 = signature2
        bean.signature3 = signature3
        bean.signature4 = signature4
        bean.signature5 = signature5
        bean.headerProtectionKey = headerProtectionKey
        bean.contentPaddingAddition = contentPaddingAddition
        bean.randomTrailers = randomTrailers
        bean.disableCookies = disableCookies
        beans.add(bean.applyDefaultValues())
    }
    if (beans.isEmpty()) error("Empty available peer list")
    return beans
}