package io.nekohasekai.sagernet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

/**
 * Гоппер VPN: метаданные сборки. versionCode в APK — это
 * VERSION_CODE из nb4a.properties, умноженный на 5
 * (см. Helpers.kt), тест закрепляет источник и согласованность.
 */
class VersionMetadataTest {

    private fun loadProps(): Properties {
        // Рабочий каталог юнит-тестов — каталог модуля app/; nb4a.properties
        // лежит в корне проекта, ищем вверх по дереву.
        var dir: java.io.File = java.io.File(System.getProperty("user.dir"))
        repeat(4) {
            val file = java.io.File(dir, "nb4a.properties")
            if (file.isFile) {
                val props = Properties()
                file.inputStream().use { props.load(it) }
                return props
            }
            dir = dir.parentFile ?: return error("nb4a.properties не найден")
        }
        error("nb4a.properties не найден")
    }

    @Test
    fun `package name is the gopper application id`() {
        assertEquals("ru.gopper.vpn", loadProps().getProperty("PACKAGE_NAME"))
    }

    @Test
    fun `version code is an integer compatible with x5 apk code`() {
        val code = loadProps().getProperty("VERSION_CODE")?.toIntOrNull()
        assertTrue("VERSION_CODE должен быть числом", code != null && code > 0)
        assertTrue(
            "код APK = VERSION_CODE*5 не должен конфликтовать с установленными сборками",
            code!! * 5 > 0,
        )
    }

    @Test
    fun `version name follows the fast-version naming`() {
        val name = loadProps().getProperty("VERSION_NAME")
        assertTrue(
            "VERSION_NAME=$name не по шаблону 1.4.2-fast-*",
            name != null && Regex("^1\\.4\\.2-fast-[A-Za-z0-9_.-]+$").matches(name),
        )
    }
}
