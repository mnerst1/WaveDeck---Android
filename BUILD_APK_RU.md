# Сборка APK в Windows

Откройте каталог `WaveDeck` в Android Studio. Через SDK Manager установите Android SDK Platform 37 и Build Tools 36.0.0 или новее. Настройте совместимый JDK для Gradle (17+; здесь использовался JDK 25 из Android Studio).

В PowerShell из корня проекта:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Первый запуск скачивает зависимости. `local.properties`, если нужен, создайте локально с `sdk.dir` через Android Studio; не коммитьте абсолютные пути.

Debug APK находится в `app\build\outputs\apk\debug\app-debug.apk`. Для тестов устройства запустите эмулятор или подключите телефон с USB debugging:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:assembleRelease
```

Release APK по умолчанию **не подписан**, R8 включён. Для личной установки проще использовать debug APK. Для публичного выпуска создайте собственный keystore через Android Studio: Build → Generate Signed App Bundle or APK → APK → Release. Храните ключ вне репозитория; секреты, пароли и `keystore.properties` нельзя добавлять в Git. Не меняйте ключ после публикации приложения.

Версии закреплены: Gradle 9.6.0, AGP 9.4.1, Kotlin Compose 2.2.20; для KSP с встроенным Kotlin требуется документированный флаг `android.disallowKotlinSourceSets=false`. Не удаляйте его без обновления KSP и повторной проверки.

`BUILD_REPORT.md` содержит фактические результаты этой сборки. Успешная сборка APK не заменяет проверку на физических устройствах.
