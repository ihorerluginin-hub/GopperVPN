package io.nekohasekai.sagernet

import io.nekohasekai.sagernet.utils.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Гоппер VPN: маппинг номеров тем на стили.
 *
 * Номера тем сохраняются в настройках (appTheme) — сдвиг любого
 * отображения ломает сохранённый у пользователя выбор, поэтому
 * маппинг закреплён тестом.
 */
class ThemeMappingTest {

    /** Все валидные номера тем дают уникальный, отличный от нуля стиль. */
    @Test
    fun `every valid theme id maps to a distinct style`() {
        val ids = listOf(
            Theme.RED, Theme.PINK_SSR, Theme.PINK, Theme.PURPLE, Theme.DEEP_PURPLE,
            Theme.INDIGO, Theme.BLUE, Theme.LIGHT_BLUE, Theme.CYAN, Theme.TEAL,
            Theme.GREEN, Theme.LIGHT_GREEN, Theme.LIME, Theme.YELLOW, Theme.AMBER,
            Theme.ORANGE, Theme.DEEP_ORANGE, Theme.BROWN, Theme.GREY,
            Theme.BLUE_GREY, Theme.BLACK, Theme.GOPPER,
        )

        assertEquals("в Theme.kt должно быть ровно 22 темы", 22, ids.size)
        assertEquals("номера тем идут подряд с 1", (1..22).toList(), ids)

        val styles = ids.map { Theme.getTheme(it) }
        assertEquals("каждой теме — свой стиль", 22, styles.distinct().size)
        styles.forEach { assertTrue("стиль не может быть 0", it != 0) }

        val dialogStyles = ids.map { Theme.getDialogTheme(it) }
        assertEquals("диалоговых стилей тоже 22 уникальных", 22, dialogStyles.distinct().size)
        dialogStyles.forEach { assertTrue(it != 0) }
    }

    /** Невалидный номер откатывается к теме по умолчанию (Гоппер). */
    @Test
    fun `invalid theme ids fall back to default`() {
        val default = Theme.getTheme(Theme.GOPPER)
        for (bad in listOf(0, -1, 23, 999, Int.MIN_VALUE)) {
            assertEquals("невалидный id $bad должен давать Гоппер", default, Theme.getTheme(bad))
            assertEquals(
                "невалидный id $bad в диалогах должен давать Гоппер",
                Theme.getDialogTheme(Theme.GOPPER),
                Theme.getDialogTheme(bad),
            )
        }
    }

    /** Палитра в colors.xml: 22 цвета, порядок совпадает с номерами тем. */
    @Test
    fun `material_colors array matches theme order`() {
        val colors = resourceText("values", "colors.xml")
        val block = Regex(
            Regex.escape("<integer-array name=\"material_colors\">") +
                "(.*?)</integer-array>",
            RegexOption.DOT_MATCHES_ALL,
        ).find(colors)?.groupValues?.get(1)
            ?: error("материал_colors не найден в colors.xml")

        val items = Regex("@color/([A-Za-z0-9_]+)").findAll(block).map { it.groupValues[1] }.toList()
        assertEquals("в палитре должно быть ровно 22 цвета", 22, items.size)

        // Порядок цветов = порядок тем (индекс элемента + 1 = номер темы).
        val expected = listOf(
            "material_red_500", "color_pink_ssr", "material_pink_500", "material_purple_500",
            "material_deep_purple_500", "material_indigo_500", "material_blue_500",
            "material_light_blue_500", "material_cyan_500", "material_teal_500",
            "material_green_500", "material_light_green_500", "material_lime_500",
            "material_yellow_500", "material_amber_500", "material_orange_500",
            "material_deep_orange_500", "material_brown_500", "material_grey_500",
            "material_blue_grey_500", "material_light_black", "gopper_500",
        )
        assertEquals(
            "порядок цветов в material_colors должен совпадать с номерами тем: " +
                "новые цвета добавляются только в конец массива",
            expected, items,
        )
    }

    /**
     * Тематические атрибуты, введённые для «тема следует деталям»,
     * объявлены и выставлены во всех нужных стилях.
     */
    @Test
    fun `theme attributes cover all styles`() {
        val themes = resourceText("values", "themes.xml")

        // 21 цветная тема + базовая = 22 значения connectingColor.
        assertEquals(
            "connectingColor должен быть у базовой темы и у каждой из 21 цветных тем",
            22, Regex("name=\"connectingColor\"").findAll(themes).count(),
        )
        // База + Black + 5 светлых палитр.
        assertEquals(
            "statsBarTextColor: база + Black + Yellow/Lime/Amber/LightGreen/Cyan",
            7, Regex("name=\"statsBarTextColor\"").findAll(themes).count(),
        )
        // 7 светлых тем переопределяют actionBarTheme тёмным оверлеем.
        assertEquals(
            "actionBarTheme с тёмным оверлеем у 7 светлых тем",
            7, Regex("actionBarTheme\">@style/ThemeOverlay\\.Gopper\\.ActionBar\\.Light").findAll(themes).count(),
        )
    }

    private fun resourceText(vararg parts: String): String {
        // Рабочий каталог юнит-тестов — каталог модуля app/; ищем res
        // вверх по дереву, чтобы тест не зависел от способа запуска.
        var dir: java.io.File = java.io.File(System.getProperty("user.dir"))
        repeat(4) {
            val res = java.io.File(dir, "src/main/res/" + parts.joinToString("/"))
            if (res.isFile) return res.readText(Charsets.UTF_8)
            dir = dir.parentFile ?: return@repeat
        }
        error("не найден ресурс: ${parts.joinToString("/")}")
    }
}
