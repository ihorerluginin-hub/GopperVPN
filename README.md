# Гоппер VPN

Android-клиент для sing-box. Форк [NekoBox for Android](https://github.com/MatsuriDayo/NekoBoxForAndroid)
версии 1.4.2 с набором собственных доработок.

![Иконка](icon-preview.png)

## Что это

Взят upstream NekoBox for Android (`MatsuriDayo/NekoBoxForAndroid`, релиз 1.4.2,
коммит `5768494d8ae3`), ничего в бэкенде не переписано — ядро, протоколы и разбор
подписок остались от upstream. Изменения только в слое приложения.

## Отличия от upstream 1.4.2

### Добавлено

| Что | Где |
|---|---|
| Кнопки **«Пинг всех»** и **«Самый быстрый»** — тест всех серверов всех подписок и переключение на лучший | `ui/ConfigurationFragment.kt`, меню `⋮` |
| Чтение QR из картинки (без камеры) | `ui/QrImageReader.kt` |
| Разворачивание подписки, завёрнутой в HTML | `group/RawUpdater.kt` |
| 6 дополнительных deep-link схем | `AndroidManifest.xml` |

### Убрано

- `ScannerActivity` и библиотека `zxing-lite` — камера не нужна
- Разрешения `CAMERA`, `READ_PHONE_STATE`
- Лаунчерный шорткат сканера

`zxing-lite` заменён на `com.google.zxing:core` — он только декодирует, камеру
не трогает.

### Исправлено

**1. R8 вырезал JNI-мостовые классы.** При сборке с обфускацией пропадали 19
классов моста `libcore`, и приложение падало на старте с `NoClassDefFoundError`.
Решение — `-keep` правила в `app/proguard-rules.pro`. Без них APK собирается,
но не запускается.

**2. Правила маршрутизации для России и Ирана.** При первом запуске
`database/ProfileManager.kt` создавал правила обхода для всех не-китайских
локалей:

```kotlin
val fuckedCountry = mutableListOf("cn:China")
if (Locale.getDefault().country != Locale.CHINA.country) {
    fuckedCountry += "ir:Iran"
    fuckedCountry += "ru:Russia"
}
```

В комплектной базе `geosite` нет кода `ru`, поэтому sing-box падал при старте:

```
create service: initialize router: parse rule-set[1]:
failed to read geosite code ru : code ru not exists!
```

В этом форке эти два правила не создаются. Правило `cn:China` осталось — вместе
с ним идёт `googleapis.cn` для Google Play.

> Если правила «Обход доменов: Россия» уже были созданы в вашей базе, они
> останутся — патч отменяет только автосоздание. Выключите или удалите их
> в разделе «Маршрут», иначе VPN не поднимется.

### Сборка

Ядро собирается в CI: `.github/workflows/libcore.yml` ставит Go и NDK,
тянет исходники sing-box на зафиксированном коммите и собирает
`libcore.aar`. Workflow из upstream (`release.yml`, `preview.yml`) удалены:
они завязаны на секрет `LOCAL_PROPERTIES` с путём SDK и паролями от ключа
подписи, которого в новом репозитории нет, и на пайплайн публикации
оригинального NekoBox.

- `local.properties` должен быть в **LF**. С CRLF остаётся лишний `\r` в
  `sdk.dir`, и AGP падает на проверке SDK.
- Линт форка не проходит, поэтому в `buildSrc/.../Helpers.kt` выставлено
  `warningsAsErrors = false`, `checkReleaseBuilds = false`.
- ABI splits отключены — собирается один универсальный APK, и набор
  поддерживаемых архитектур задаётся составом `libcore.aar`. Сейчас это
  `arm64-v8a` и `armeabi-v7a`. Четыре архитектуры раздували ядро до 36 МБ и
  APK до 41,8 МБ, с двумя — 23,6 МБ.
- `libcore.aar` в репозитории нет и перечислен в `.gitignore`: весит 18 МБ и
  зависит от коммитов чужих исходников. Собирается в CI либо локально.

Порядок сборки локально:

```
D:\vk\build-v3.ps1
```

Скрипт ставит `JAVA_HOME`, `GRADLE_USER_HOME` и `ANDROID_HOME`, собирает
`libcore.aar`, затем запускает `assembleOssRelease`.

Готовый APK: `app/build/outputs/oss/release/NekoBox-1.4.2-fast-v3.apk`.

## Как ставить

Package name — `ru.gopper.vpn`, он отличается от официального `moe.nb4a`,
поэтому приложение ставится **рядом** с NekoBox и не конфликтует с ним.

Из-за смены package обновление поверх ранее установленной версии не получится:
Android считает их разными приложениями. Перед переходом выгрузи подписки
(Настройки → Экспорт), после установки залей их обратно (Импорт).

### Менять package

Одно место — `nb4a.properties`:

```
PACKAGE_NAME=ru.gopper.vpn
```

Это `applicationId`. `namespace` в `app/build.gradle.kts` остаётся
`io.nekohasekai.sagernet` — он генерирует классы `R` и `BuildConfig`,
его трогать нельзя без переписывания всех импортов.

## Лицензия

**GPL-3.0.** Исходный проект — [NekoBox for Android](https://github.com/MatsuriDayo/NekoBoxForAndroid),
автор nekohasekai. Лицензия и авторство сохранены, изменения перечислены выше.

Иконка (`make_icon.ps1`) сделана для этого форка.

## Логи

sing-box пишет логи не в logcat, а в файл `neko.log` внутри кэша приложения.
Читать — на экране «Журналы». Уровень задаётся в
Настройки → «Тип журнала» (`none` / `warn` / `info` / `debug` / `trace`).
