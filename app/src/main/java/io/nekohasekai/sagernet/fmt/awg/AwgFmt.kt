package io.nekohasekai.sagernet.fmt.awg

import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.withPrefixLength
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.listByLineOrComma
import org.ini4j.Ini
import org.ini4j.Profile.Section
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
        local_address = bean.localAddress.listByLineOrComma().map { it.withPrefixLength() }
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
/**
 * Поиск значения по ключу без учёта регистра.
 *
 * Принимает Map, а не Section: интерфейс Section наследует Map сразу
 * через MultiMap и CommentedMap, и на нём Kotlin не может выбрать
 * перегрузку get. Явное приведение снимает вопрос.
 */
private fun lookup(section: Section, key: String): String? {
    val map = section as Map<String, String>
    map[key]?.let { if (it.isNotBlank()) return it }
    for (k in map.keys) {
        if (!k.equals(key, ignoreCase = true)) continue
        val found = map[k]
        if (!found.isNullOrBlank()) return found
    }
    return null
}

private fun intValue(section: Section, key: String): Int? = lookup(section, key)?.trim()?.toIntOrNull()

private fun boolValue(section: Section, key: String): Boolean {
    val raw = lookup(section, key) ?: return false
    return raw.equals("true", ignoreCase = true) || raw == "1"
}

/** Есть ли в секции [Interface] хоть один ключ, которого нет в обычном WireGuard. */
fun isAwgIni(ini: Ini): Boolean {
    val iface = ini["Interface"] ?: return false
    return AWG_MARKER_KEYS.any { lookup(iface, it) != null }
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
    val privateKey = lookup(iface, "PrivateKey").orEmpty()
    val mtu = lookup(iface, "MTU")?.toIntOrNull()

    val junkCount = lookup(iface, "Jc")?.toIntOrNull() ?: 0
    val junkMin = lookup(iface, "Jmin")?.toIntOrNull() ?: 0
    val junkMax = lookup(iface, "Jmax")?.toIntOrNull() ?: 0

    val initPadding = lookup(iface, "S1")?.toIntOrNull() ?: 0
    val responsePadding = lookup(iface, "S2")?.toIntOrNull() ?: 0
    val cookiePadding = lookup(iface, "S3")?.toIntOrNull() ?: 0
    val transportPadding = lookup(iface, "S4")?.toIntOrNull() ?: 0

    val initHeader = lookup(iface, "H1").orEmpty()
    val responseHeader = lookup(iface, "H2").orEmpty()
    val cookieHeader = lookup(iface, "H3").orEmpty()
    val transportHeader = lookup(iface, "H4").orEmpty()

    val signature1 = lookup(iface, "I1").orEmpty()
    val signature2 = lookup(iface, "I2").orEmpty()
    val signature3 = lookup(iface, "I3").orEmpty()
    val signature4 = lookup(iface, "I4").orEmpty()
    val signature5 = lookup(iface, "I5").orEmpty()

    val headerProtectionKey = lookup(iface, "HeaderProtectionKey").orEmpty()
    val contentPaddingAddition = lookup(iface, "ContentPaddingAddition").orEmpty()
    val randomTrailers = boolValue(iface, "RandomTrailers")
    val disableCookies = boolValue(iface, "DisableCookies")

    val beans = mutableListOf<AwgBean>()
    for (peer in peers) {
        val endpoint = lookup(peer, "Endpoint")
        if (endpoint.isNullOrBlank() || !endpoint.contains(":")) continue

        // PersistentKeepalive в AWG допускает диапазон «25-30». Бин держит
        // целое, поэтому берём левую границу. Молча выбрасывать нельзя:
        // без keepalive NAT-соединение будет рваться по таймауту.
        val keepalive = lookup(peer, "PersistentKeepalive")
            ?.substringBefore('-')?.trim()?.toIntOrNull() ?: 0

        val peerPort = endpoint.substringAfterLast(":").toIntOrNull() ?: continue
        val peerKey = lookup(peer, "PublicKey") ?: continue

        val bean = AwgBean()
        bean.serverAddress = endpoint.substringBeforeLast(":")
        bean.serverPort = peerPort
        bean.peerPublicKey = peerKey
        bean.peerPreSharedKey = lookup(peer, "PresharedKey").orEmpty()
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