# FitRecord V1 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付一个纯本地、无广告的健身打卡 Android App，在 `d:\桌面（2）\FitRecord\android` 用命令行产出**已自签名的 `app-release.apk`**。

**Architecture:** 单 Activity + Jetpack Compose + Navigation-Compose；MVVM，每屏一个 ViewModel，Room 的 `Flow` 直接驱动 UI；统计全部在 SQL 层聚合；依赖用手写 `AppContainer` 注入（无 Hilt）。

**Tech Stack:** Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.9 · Compose BOM 2024.12.01 / Material3 1.3.1 · Room 2.6.1 + KSP 2.0.21-1.0.28 · DataStore 1.1.1 · navigation-compose 2.8.4 · kotlinx-serialization-json 1.7.3 · coroutines 1.9.0 · JUnit4 + Robolectric 4.14.1

**Spec:** `docs/plan/2026-10-12-fitrecord-ui-design.md`（UI 与领域口径的唯一事实来源）
**环境结论:** `技术选型.md`（本机工具链实测）

---

## Global Constraints

每个任务都隐含遵守以下各条（数值逐字摘自规格与技术选型）：

- JDK **只能**用 `D:\insbuild\jdk17\jdk-17.0.20.1+1`。系统 `JAVA_HOME` 指向不存在的 `C:\Program Files\Java\jdk-14.0.2`，任何 Gradle 调用前必须先设 `$env:JAVA_HOME`（见 Task 1 的 `env.ps1`）。
- `ANDROID_HOME = D:\insbuild\android-sdk`；`local.properties` 写 `sdk.dir=D:/insbuild/android-sdk`（正斜杠）。
- 版本：`compileSdk 35` / `targetSdk 35` / `minSdk 26` / `jvmTarget 17` / Gradle wrapper **8.9**。
- 包名 `com.fitrecord.app`，应用显示名「轻体记」。
- **零网络**：Manifest 中不得出现 `android.permission.INTERNET`；不引入任何需要网络的运行期功能（D11）。
- **不引入**：Hilt、Coil/Glide、material-icons-extended、第三方图表库、react-native、SQLCipher、NDK 相关插件。图标只用 `material-icons-core`（随 material3 传递依赖）+ emoji。
- **V1 不做**（D7）：图表导出 PNG / 长图报告、农历、动作级力量记录、桌面小组件、每周目标进度环（`weeklyGoal` 字段只存不显示）。
- 数据库**不加密**；**禁止** `fallbackToDestructiveMigration()`；`exportSchema = true`，schema JSON 落 `app/schemas/` 并纳入版本控制。
- release 构建 `isMinifyEnabled = false`（V1 关 R8）。
- 日期一律以 `epochDay: Int` 存库，UI 层用 `java.time.LocalDate`（minSdk 26 原生支持，无需脱糖）。
- 颜色/字号/间距/圆角**只能**取自 `ui/theme/` 的 token，禁止在屏幕文件里写字面量颜色（规格 2.1–2.3）。
- 所有破坏性操作走 `ConfirmDialog`，确认按钮 `error` 色（规格 5.4）。
- Shell 是 PowerShell：**语句分隔用 `;`，不能用 `&&`**。
- 测试跑在 JVM 上（Robolectric，`@Config(sdk = [34])`）；**不使用** `androidTest`（本机无设备/模拟器）。

### 构建与测试命令约定（全计划通用）

```powershell
# 每次开新终端先执行（Task 1 会创建它）
. "d:\桌面（2）\FitRecord\android\env.ps1"

cd "d:\桌面（2）\FitRecord\android"
.\gradlew.bat :app:testDebugUnitTest --tests "*CalorieCalculatorTest"   # 跑单个测试类
.\gradlew.bat :app:testDebugUnitTest                                    # 全量单测
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

### 文件结构总览（先定边界，再拆任务）

```
android/
├── env.ps1                        构建环境变量（Task 1）
├── settings.gradle.kts  build.gradle.kts  gradle.properties
├── keystore.properties.example    签名配置模板（Task 4）
├── gradle/libs.versions.toml      版本目录（Task 2）
├── gradle/wrapper/                Gradle 8.9 wrapper（Task 2）
└── app/
    ├── build.gradle.kts           含签名 / Room schema / 测试配置（Task 2、4）
    ├── schemas/…/1.json           Room 导出的 schema（Task 5 生成）
    └── src/
        ├── main/AndroidManifest.xml
        ├── main/res/values/{strings.xml,colors.xml,themes.xml}
        └── main/java/com/fitrecord/app/
            ├── FitRecordApp.kt            Application，持有 AppContainer
            ├── AppContainer.kt            手写 DI：db / dao / repo / settings
            ├── LocalContainer.kt          CompositionLocal<AppContainer>
            ├── MainActivity.kt            单 Activity，Scaffold + NavHost
            ├── core/WeekUtil.kt           epochDay / 周月区间 / 网格
            ├── data/db/
            │   ├── FitRecordDatabase.kt   @Database + MIGRATIONS + callback
            │   ├── Converters.kt
            │   ├── entity/AllEntities.kt  8 个 @Entity（一文件，字段互相关联）
            │   ├── dao/{TrainingDao,TypeDao,StatsDao,NoteDao,ProfileDao,MetaDao,SeedDao}.kt
            │   └── seed/{BuiltinExercises,BodyParts,Foods,MetReference}.kt
            ├── data/prefs/SettingsStore.kt
            ├── data/repo/{TrainingRepository,TypeRepository,StatsRepository,ProfileRepository,BackupRepository}.kt
            ├── domain/{CalorieCalculator,FoodEquivalent,CalendarBuilder,StatsModels}.kt
            ├── domain/backup/{BackupModels,BackupCodec,CsvCodec,MergeResolver}.kt
            ├── ui/theme/{Color,Dimens,Type,FitRecordTheme}.kt
            ├── ui/nav/{Routes,FitRecordNavHost}.kt
            ├── ui/components/{SegmentedSwitch,DayCell,MetricCard,RecordCard,TagSelector,
            │                  MinuteField,EmptyState,ConfirmDialog,InfoBanner,Sheet}.kt
            ├── ui/charts/{ChartModel,BarChart,LineChart,DonutChart,RadarChart,Heatmap}.kt
            └── ui/{onboarding,calendar,stats,profile,day,record,body,types,backup,appearance}/
                ├── XxxScreen.kt
                └── XxxViewModel.kt
        └── test/java/com/fitrecord/app/…  与 main 包结构镜像
```

---

## M0 工程骨架与可安装包

### Task 1: 仓库初始化与构建环境脚本

**Files:**
- Create: `d:\桌面（2）\FitRecord\.gitignore`
- Create: `d:\桌面（2）\FitRecord\android\env.ps1`
- Create: `d:\桌面（2）\FitRecord\android\local.properties`

**Interfaces:**
- Consumes: 无
- Produces: `env.ps1` 被后续所有构建步骤 dot-source；`local.properties` 被 Gradle 读取定位 SDK

- [ ] **Step 1: 初始化 git 仓库（本机私有，不做任何 push）**

```powershell
cd "d:\桌面（2）\FitRecord"
git init
git config user.name  "fitrecord-dev"
git config user.email "fitrecord-dev@local"
```

- [ ] **Step 2: 写 `.gitignore`**

```
android/.gradle/
android/build/
android/app/build/
android/local.properties
android/keystore.properties
android/*.keystore
android/*.jks
.idea/
*.iml
.DS_Store
```

- [ ] **Step 3: 写 `android/env.ps1`（关键：覆盖失效的 JAVA_HOME）**

```powershell
# 用法：在 PowerShell 里执行  . .\env.ps1   （注意前面的点和空格）
$env:JAVA_HOME    = "D:\insbuild\jdk17\jdk-17.0.20.1+1"
$env:ANDROID_HOME = "D:\insbuild\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;" + $env:Path
Write-Host "JAVA_HOME    = $env:JAVA_HOME"
Write-Host "ANDROID_HOME = $env:ANDROID_HOME"
java -version
```

- [ ] **Step 4: 写 `android/local.properties`**

```
sdk.dir=D:/insbuild/android-sdk
```

- [ ] **Step 5: 验证环境**

Run: `cd "d:\桌面（2）\FitRecord\android"; . .\env.ps1; adb version`
Expected: 打印 `JAVA_HOME = D:\insbuild\jdk17\...`、`openjdk version "17.0.20.1"`、`Android Debug Bridge version 1.0.41`
若 `java` 不是 17：检查 `env.ps1` 路径拼写，不要改系统环境变量。

- [ ] **Step 6: Commit**

```powershell
cd "d:\桌面（2）\FitRecord"; git add .gitignore android/env.ps1; git commit -m "chore: git init + build env script (JDK17/SDK35)"
```

---

### Task 2: Gradle 工程与空 Compose 应用

**Files:**
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`
- Create: `android/gradle/libs.versions.toml`
- Create: `android/gradle/wrapper/gradle-wrapper.properties` + `gradle-wrapper.jar` + `android/gradlew` + `android/gradlew.bat`（从 `D:\insbuild\proj` 复制）
- Create: `android/app/build.gradle.kts`, `android/app/proguard-rules.pro`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/com/fitrecord/app/{FitRecordApp.kt,MainActivity.kt}`
- Create: `android/app/src/main/res/values/{strings.xml,themes.xml}`

**Interfaces:**
- Consumes: Task 1 的 `env.ps1`、`local.properties`
- Produces: 可执行 `:app:assembleDebug`；`FitRecordApp`（后续 Task 加 `container` 属性）、`MainActivity`（后续 Task 换成 NavHost）

- [ ] **Step 1: 复制 Gradle wrapper（复用本机已缓存的 8.9 发行版，避免重新下载）**

```powershell
cd "d:\桌面（2）\FitRecord\android"
New-Item -ItemType Directory -Force -Path gradle\wrapper | Out-Null
Copy-Item "D:\insbuild\proj\gradle\wrapper\gradle-wrapper.jar"  gradle\wrapper\
Copy-Item "D:\insbuild\proj\gradle\wrapper\gradle-wrapper.properties" gradle\wrapper\
Copy-Item "D:\insbuild\proj\gradlew"     .
Copy-Item "D:\insbuild\proj\gradlew.bat" .
Get-Content gradle\wrapper\gradle-wrapper.properties | Select-String distributionUrl
```
Expected: `distributionUrl=...gradle-8.9-bin.zip`

- [ ] **Step 2: 写 `gradle/libs.versions.toml`（逐字使用，版本已实测可达）**

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
navigationCompose = "2.8.4"
room = "2.6.1"
datastore = "1.1.1"
serializationJson = "1.7.3"
coroutines = "1.9.0"
junit = "4.13.2"
androidxTestCore = "1.6.1"
robolectric = "4.14.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serializationJson" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-test-core = { group = "androidx.test", name = "core", version.ref = "androidxTestCore" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 3: 写 `settings.gradle.kts` 与顶层 `build.gradle.kts`**

`settings.gradle.kts`：
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "FitRecord"
include(":app")
```

`build.gradle.kts`：
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 4: 写 `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.java.home=D:/insbuild/jdk17/jdk-17.0.20.1+1
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```
`org.gradle.java.home` 是**双保险**：即使忘记 dot-source `env.ps1` 也能构建。路径用正斜杠。

- [ ] **Step 5: 写 `app/build.gradle.kts`（本任务先不含签名，Task 4 再加）**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.fitrecord.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fitrecord.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false   // V1 关闭 R8（技术选型）
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true   // Robolectric + Compose 必需
            isReturnDefaultValues = true
        }
    }
    sourceSets { getByName("test").assets.srcDirs("$projectDir/schemas") }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
```

`app/proguard-rules.pro`：
```
# V1 未开启 R8；保留规则占位，V1.1 开启混淆时补 Room/kotlinx-serialization 规则
-keepattributes *Annotation*
```

- [ ] **Step 6: 写 Manifest、资源与最小应用代码**

`app/src/main/AndroidManifest.xml`（**注意：不声明 INTERNET**）：
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:name=".FitRecordApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="false"
        android:theme="@style/Theme.FitRecord">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`res/values/strings.xml`：
```xml
<resources>
    <string name="app_name">轻体记</string>
</resources>
```

`res/values/themes.xml`：
```xml
<resources>
    <style name="Theme.FitRecord" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

`FitRecordApp.kt`：
```kotlin
package com.fitrecord.app

import android.app.Application

class FitRecordApp : Application()
```

`MainActivity.kt`：
```kotlin
package com.fitrecord.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("FitRecord skeleton")
            }
        }
    }
}
```

- [ ] **Step 7: 补一个可运行的启动图标（避免 Manifest 引用缺失）**

```powershell
cd "d:\桌面（2）\FitRecord\android"
New-Item -ItemType Directory -Force -Path app\src\main\res\mipmap-anydpi-v26, app\src\main\res\drawable, app\src\main\res\values | Out-Null
```
`res/drawable/ic_launcher_foreground.xml`：
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#2E9E5B" android:pathData="M34,60h8v-8h24v8h8v-12h-8v-6h-24v6h-8z"/>
</vector>
```
`res/values/colors.xml`：
```xml
<resources><color name="ic_launcher_background">#FFFFFF</color></resources>
```
`res/mipmap-anydpi-v26/ic_launcher.xml`：
```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
```
`res/mipmap-anydpi-v26/ic_launcher_round.xml`：内容与 `ic_launcher.xml` 完全相同（两个文件都要建，否则圆角桌面图标回退成默认绿方块）。

- [ ] **Step 8: 构建并验证 APK 产出**

Run: `cd "d:\桌面（2）\FitRecord\android"; . .\env.ps1; .\gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`，且 `app\build\outputs\apk\debug\app-debug.apk` 存在。
首次会下载 KSP / room-compiler / navigation-compose（约 1–3 分钟）。
若报 `Unsupported class file major version` → `JAVA_HOME` 未生效，回到 Step 5 检查 `org.gradle.java.home`。

- [ ] **Step 9: Commit**

```powershell
cd "d:\桌面（2）\FitRecord"; git add android; git commit -m "feat(m0): gradle skeleton (AGP 8.7.3 / Kotlin 2.0.21 / Compose / Room deps) builds debug apk"
```

---

### Task 3: 主题、色板与深色模式基座

**Files:**
- Create: `app/src/main/java/com/fitrecord/app/ui/theme/Color.kt`、`Dimens.kt`、`Type.kt`、`FitRecordTheme.kt`
- Modify: `app/src/main/java/com/fitrecord/app/MainActivity.kt`
- Test: `app/src/test/java/com/fitrecord/app/ui/theme/ThemeTokensTest.kt`

**Interfaces:**
- Consumes: Task 2 的工程
- Produces:
  - `AppColors.light: ColorScheme` / `AppColors.dark: ColorScheme`
  - `ExercisePalette.colors: List<Color>`（8 色，索引即 `typeId % 8`）
  - `Dimens`（`pagePadding=16.dp`、`cardGap=12.dp`、`cardPadding=16.dp`、`cardCorner=16.dp`、`chipCorner=8.dp`、`dayCellHeightWeek=44.dp`、`dayCellHeightMonth=64.dp`、`navBarHeight=64.dp`、`fabSize=56.dp`、`minTouchTarget=48.dp`、`chartHeight=180.dp`）
  - `FitRecordTextTheme`（display 22 / title 17 / body 14 / label 13 / caption 11 / numberLg 28）
  - `@Composable fun FitRecordTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit)`
  - `enum class ThemeMode { SYSTEM, LIGHT, DARK }`（Task 11 的 SettingsStore 复用此枚举 → 定义在 `ui/theme/FitRecordTheme.kt`）

- [ ] **Step 1: 先写失败测试 `ThemeTokensTest.kt`**

```kotlin
package com.fitrecord.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTokensTest {
    @Test fun `primary is brand green in light scheme`() {
        assertEquals(Color(0xFF2E9E5B), AppColors.light.primary)
    }
    @Test fun `secondary is brand orange`() {
        assertEquals(Color(0xFFF5A623), AppColors.light.secondary)
    }
    @Test fun `exercise palette has 8 colors and no duplicate`() {
        assertEquals(8, ExercisePalette.colors.size)
        assertEquals(8, ExercisePalette.colors.distinct().size)
    }
    @Test fun `palette index wraps for arbitrary type id`() {
        assertEquals(ExercisePalette.colors[0], ExercisePalette.colorForId(8))
        assertEquals(ExercisePalette.colors[3], ExercisePalette.colorForId(-5))   // 负数也要安全
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ThemeTokensTest"`
Expected: 编译失败（`Unresolved reference: AppColors`）——这就是本步的"红"。

- [ ] **Step 3: 写 `ui/theme/Color.kt`（逐字对应规格 2.1）**

```kotlin
package com.fitrecord.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Green   = Color(0xFF2E9E5B); private val GreenD  = Color(0xFF4FBE7E)
private val OnGreen = Color(0xFFFFFFFF); private val OnGreenD = Color(0xFF07210F)
private val GreenCt = Color(0xFFDCF2E4); private val GreenCtD = Color(0xFF123A22)
private val Orange  = Color(0xFFF5A623); private val OrangeD = Color(0xFFF7BE55)
private val OnOrange = Color(0xFF3A2400)
private val Bg      = Color(0xFFF5F6F7); private val BgD     = Color(0xFF101114)
private val Surf    = Color(0xFFFFFFFF); private val SurfD   = Color(0xFF1A1C20)
private val SurfVar = Color(0xFFF1F2F4); private val SurfVarD = Color(0xFF23262B)
private val OnSurf  = Color(0xFF1B1C1E); private val OnSurfD = Color(0xFFE3E3E6)
private val OnSurfVar = Color(0xFF6B7075); private val OnSurfVarD = Color(0xFFA5AAAF)
private val OutlineC  = Color(0xFFDDE0E3); private val OutlineCD = Color(0xFF33373C)
private val ErrorC    = Color(0xFFC62828); private val ErrorCD   = Color(0xFFFF8A80)
private val DisabledC = Color(0xFFBFC3C6); private val DisabledCD = Color(0xFF5A5F63)
private val UnreadC   = Color(0xFFB6BABD); private val UnreadCD  = Color(0xFF5F6468)

object AppColors {
    val light: ColorScheme = lightColorScheme(
        primary = Green, onPrimary = OnGreen, primaryContainer = GreenCt,
        secondary = Orange, onSecondary = OnOrange,
        background = Bg, onBackground = OnSurf,
        surface = Surf, onSurface = OnSurf,
        surfaceVariant = SurfVar, onSurfaceVariant = OnSurfVar,
        outline = OutlineC, error = ErrorC, onError = Color(0xFFFFFFFF),
    )
    val dark: ColorScheme = darkColorScheme(
        primary = GreenD, onPrimary = OnGreenD, primaryContainer = GreenCtD,
        secondary = OrangeD, onSecondary = OnOrange,
        background = BgD, onBackground = OnSurfD,
        surface = SurfD, onSurface = OnSurfD,
        surfaceVariant = SurfVarD, onSurfaceVariant = OnSurfVarD,
        outline = OutlineCD, error = ErrorCD, onError = Color(0xFF2B0000),
    )
    /** 规格 2.1 的"未健身"灰字，Material3 无对应槽位，单独暴露 */
    val unreadLight = UnreadC
    val unreadDark  = UnreadCD
    val disabledLight = DisabledC
    val disabledDark  = DisabledCD
}

object ExercisePalette {
    /** 划船机 椭圆机 动感单车 跑步机 自由力量 瑜伽 快走 游泳（规格 2.1） */
    val colors = listOf(
        Color(0xFF2E9E5B), Color(0xFF3E8FD6), Color(0xFFE5645C), Color(0xFFF5A623),
        Color(0xFF8E6BE8), Color(0xFF20B2AA), Color(0xFFD9772E), Color(0xFF5C7CC9),
    )
    /** 自定义类型的颜色按 id 轮询（规格 2.1）；用 floorMod 保证负 id 也不越界 */
    fun colorForId(id: Long): Color = colors[java.lang.Math.floorMod(id.toInt(), colors.size)]
}
```

同文件末尾追加颜色 ↔ 存库 Long 的转换扩展：
```kotlin
package com.fitrecord.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

fun Color.toRgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
fun Long.toColor(): Color = Color(this.toInt())
```
（存库用 `Long` ARGB，取出来渲染用 `Color`；写进 `Color.kt` 末尾。）

- [ ] **Step 4: 写 `Dimens.kt` / `Type.kt` / `FitRecordTheme.kt`**

`Dimens.kt`：
```kotlin
package com.fitrecord.app.ui.theme

import androidx.compose.ui.unit.dp

object Dimens {
    val pagePadding = 16.dp
    val cardGap = 12.dp
    val cardPadding = 16.dp
    val cardCorner = 16.dp
    val chipCorner = 8.dp
    val dayCellHeightWeek = 44.dp
    val dayCellHeightMonth = 64.dp
    val navBarHeight = 64.dp
    val fabSize = 56.dp
    val minTouchTarget = 48.dp
    val chartHeight = 180.dp
    val radarSize = 200.dp
    val donutSize = 160.dp
    val sparklineHeight = 48.dp
    val dotSize = 6.dp
    val selectedCircle = 40.dp
}
```

`Type.kt`：
```kotlin
package com.fitrecord.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val FitRecordTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium),   // display
    titleLarge    = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),   // title
    bodyMedium    = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),   // body
    labelLarge    = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),   // label
    labelSmall    = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal),   // caption
)
```
（`numberLg` 28sp 由 `MetricCard` 用 `MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp)` 组合，不新增 token。）

`FitRecordTheme.kt`：
```kotlin
package com.fitrecord.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode { SYSTEM, LIGHT, DARK }

val LocalUnreadColor = staticCompositionLocalOf { Color.Gray }

@Composable
fun FitRecordTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = if (dark) AppColors.dark else AppColors.light
    val unread = if (dark) AppColors.unreadDark else AppColors.unreadLight
    CompositionLocalProvider(LocalUnreadColor provides unread) {
        MaterialTheme(colorScheme = scheme, typography = FitRecordTypography, shapes = FitRecordShapes, content = content)
    }
}
```
`Shapes.kt`（被上面引用，必须同时创建）：
```kotlin
package com.fitrecord.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val FitRecordShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(Dimens.chipCorner),
    medium = RoundedCornerShape(Dimens.cardCorner),
    large = RoundedCornerShape(Dimens.cardCorner),
    extraLarge = RoundedCornerShape(24.dp),
)
```

- [ ] **Step 5: MainActivity 接入主题 + 写一条 UI 冒烟测试**

`MainActivity.kt` 的 `setContent` 改为：
```kotlin
setContent {
    FitRecordTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("FitRecord skeleton", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
```
新增 import：`androidx.compose.material3.{MaterialTheme,Surface,Text}`、`com.fitrecord.app.ui.theme.FitRecordTheme`。

`app/src/test/java/com/fitrecord/app/ui/theme/ThemeRenderTest.kt`：
```kotlin
package com.fitrecord.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ThemeRenderTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `dark mode applies dark scheme background`() {
        rule.setContent {
            FitRecordTheme(themeMode = ThemeMode.DARK) {
                Text("probe", color = MaterialTheme.colorScheme.background)
            }
        }
        rule.onNodeWithText("probe").assertExists()
    }

    @Test fun `light mode provides the unread color via composition local`() {
        rule.setContent {
            FitRecordTheme(themeMode = ThemeMode.LIGHT) {
                Text("unread", color = LocalUnreadColor.current)
            }
        }
        rule.onNodeWithText("unread").assertExists()
    }
}
```
import 需含：`androidx.compose.material3.MaterialTheme`、`androidx.compose.material3.Text`、
`androidx.compose.ui.test.junit4.createComposeRule`、`androidx.compose.ui.test.onNodeWithText`、
`com.fitrecord.app.ui.theme.{FitRecordTheme,LocalUnreadColor,ThemeMode}`。

- [ ] **Step 6: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ThemeTokensTest" --tests "*ThemeRenderTest"`
Expected: 2 个测试类全绿（ThemeTokensTest 4 个 + ThemeRenderTest 2 个）。

- [ ] **Step 7: Commit**

```powershell
git add android; git commit -m "feat(m0): design tokens (light/dark palette, dimens, type) + theme base"
```

---

### Task 4: release 自签名与签名验证

**Files:**
- Create: `android/keystore.properties.example`
- Create（执行 keytool 生成，不入库）: `android/fitrecord-release.keystore`、`android/keystore.properties`
- Modify: `android/app/build.gradle.kts`（加 signingConfigs）
- Modify: `技术选型.md` 5.3 节（若口令策略有变）

**Interfaces:**
- Consumes: Task 2 的 `app/build.gradle.kts`
- Produces: `assembleRelease` 产出**已用 release key 签名**的 APK；`signingConfig` 缺失时回退 debug 签名并打印警告

- [ ] **Step 1: 生成 keystore（口令自行留存，此文件绝不提交）**

```powershell
cd "d:\桌面（2）\FitRecord\android"; . .\env.ps1
keytool -genkeypair -v -keystore fitrecord-release.keystore -alias fitrecord `
  -keyalg RSA -keysize 2048 -validity 10950 `
  -storepass "FitRecord#2026" -keypass "FitRecord#2026" `
  -dname "CN=FitRecord, OU=Dev, O=FitRecord, L=NA, S=NA, C=CN"
```
Expected: 生成 `fitrecord-release.keystore`，打印 `Generating key pair`。

- [ ] **Step 2: 写 `keystore.properties` 与模板**

`keystore.properties`（本地真实值，被 `.gitignore` 忽略）：
```properties
storeFile=fitrecord-release.keystore
storePassword=FitRecord#2026
keyAlias=fitrecord
keyPassword=FitRecord#2026
```
`keystore.properties.example`（入库，值用占位说明）：
```properties
# 复制为 keystore.properties 后填入真实值；口令丢失将无法覆盖升级
storeFile=fitrecord-release.keystore
storePassword=<你的库口令>
keyAlias=fitrecord
keyPassword=<你的键口令>
```

- [ ] **Step 3: 在 `app/build.gradle.kts` 加签名逻辑（失败可回退，保证任何机器都能出包）**

在 `android {` 块**之前**插入：
```kotlin
import java.util.Properties

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKey = keystorePropsFile.exists() &&
    rootProject.file(keystoreProps.getProperty("storeFile") ?: "").exists()
```
在 `android { }` 内 `buildTypes` **之前**插入：
```kotlin
signingConfigs {
    if (hasReleaseKey) {
        create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
}
```
把 `buildTypes.release` 改为：
```kotlin
release {
    isMinifyEnabled = false
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
}
```
在文件末尾追加：
```kotlin
if (!hasReleaseKey) {
    logger.warn("WARN: keystore.properties 缺失，release 将回退 debug 签名（仅可用于自测，不可覆盖升级）")
}
```

- [ ] **Step 4: 构建 release 并用 apksigner 验证签名者**

```powershell
.\gradlew.bat :app:assembleRelease
& "D:\insbuild\android-sdk\build-tools\35.0.0\apksigner.bat" verify --print-certs `
  "app\build\outputs\apk\release\app-release.apk"
```
Expected: `Verifies`；`Signer #1 certificate DN: CN=FitRecord, OU=Dev, O=FitRecord, L=NA, S=NA, C=CN`
（若 DN 是 Android debug key，说明 `hasReleaseKey` 为 false → 检查 `keystore.properties` 的 `storeFile` 相对路径）

- [ ] **Step 5: 断言 Manifest 无网络权限（DoD 第 8 条）**

```powershell
& "D:\insbuild\android-sdk\build-tools\35.0.0\aapt2.exe" dump permissions "app\build\outputs\apk\release\app-release.apk"
```
Expected: 输出中**不含** `permission.INTERNET`；允许出现 `android.permission.DUMP` 之类的系统调试权限为异常，应排查。

- [ ] **Step 6: Commit**

```powershell
git add android/app/build.gradle.kts android/keystore.properties.example
git commit -m "feat(m0): release signing with debug fallback + apksigner verification"
```

---

## M1 数据层与领域逻辑

### Task 5: Room 实体、DAO 与数据库

**Files:**
- Create: `app/src/main/java/com/fitrecord/app/data/db/entity/AllEntities.kt`
- Create: `app/src/main/java/com/fitrecord/app/data/db/dao/{TypeDao,TrainingDao,StatsDao,NoteDao,ProfileDao,FoodDao,MetaDao}.kt`
- Create: `app/src/main/java/com/fitrecord/app/data/db/{FitRecordDatabase.kt,StatsRows.kt}`
- Create: `app/src/main/java/com/fitrecord/app/AppContainer.kt`、`LocalContainer.kt`
- Modify: `app/src/main/java/com/fitrecord/app/FitRecordApp.kt`
- Test: `app/src/test/java/com/fitrecord/app/data/db/DatabaseSmokeTest.kt`

**Interfaces:**
- Consumes: Task 2 的 Room/KSP 配置；Task 3 的 `Color.toRgbLong()`
- Produces:
  - `AppContainer(ctx: Context)` 暴露 `db`、`trainingDao`、`typeDao`、`statsDao`、`noteDao`、`profileDao`、`foodDao`、`metaDao`
  - `LocalContainer: ProvidableCompositionLocal<AppContainer>`
  - 实体类名与字段（规格 4.2）：`ExerciseTypeEntity(id,name,met,color:Long,isBuiltin,archived,metOverridden,sortOrder,defaultBodyPartIds)`、`BodyPartEntity(id,name,sortOrder)`、`TrainingRecordEntity(id,date:Int,exerciseTypeId,durationMin,calories:Double?,note,createdAt,updatedAt)`、`RecordBodyPartEntity(recordId,bodyPartId)`、`DailyNoteEntity(date:Int,text,updatedAt)`、`UserProfileEntity(id=0,gender:String?,age:Int?,heightCm:Double?,weightKg:Double,weeklyGoal:Int=0,updatedAt)`、`FoodItemEntity(id,name,emoji,unitLabel,kcalPerUnit)`、`MetaEntity(key,value)`
  - `StatsRows`：`PerDayRow(date,segments,minutes)`、`PerTypeRow(typeId,name,color,minutes,segments)`、`PerPartRow(partId,name,segments)`

- [ ] **Step 1: 写失败测试 `DatabaseSmokeTest.kt`**

```kotlin
package com.fitrecord.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DatabaseSmokeTest {

    private fun db() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), FitRecordDatabase::class.java
    ).allowMainThreadQueries().build()

    @Test fun `database has 8 tables`() = runBlocking {
        val d = db()
        val tables = d.supportOpenHelper.writableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
        ).let { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        assertTrue("实际表: $tables", tables.containsAll(listOf(
            "exercise_type","body_part","training_record","record_body_part",
            "daily_note","user_profile","food_item","meta")))
        d.close()
    }

    @Test fun `inserting record then observing range returns it`() = runBlocking {
        val d = db()
        d.typeDao().insertType(name = "划船机", met = 7.0, color = 0xFF2E9E5BL, isBuiltin = true, sortOrder = 0)
        val typeId = d.typeDao().all().first().first().id
        d.trainingDao().deleteAll()
        d.trainingDao().insert(TrainingRecordEntity(date = 20000, exerciseTypeId = typeId,
            durationMin = 45, calories = 320.0, note = "", createdAt = 1, updatedAt = 1))
        val rows = d.trainingDao().observeRange(19990, 20010).first()
        assertEquals(1, rows.size)
        assertEquals(45, rows.first().durationMin)
        d.close()
    }
}
```

- [ ] **Step 2: 跑测试确认编译失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*DatabaseSmokeTest"`
Expected: `Unresolved reference: FitRecordDatabase`

- [ ] **Step 3: 写 `entity/AllEntities.kt`**

```kotlin
package com.fitrecord.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_type")
data class ExerciseTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(unique = true) val name: String,
    val met: Double,
    val color: Long,
    val isBuiltin: Boolean,
    val archived: Boolean = false,
    val metOverridden: Boolean = false,
    val sortOrder: Int = 0,
    /** 逗号分隔的 body_part.id，P8 的"默认部位" */
    val defaultBodyPartIds: String = "",
)

@Entity(tableName = "body_part")
data class BodyPartEntity(@PrimaryKey val id: Long, val name: String, val sortOrder: Int)

@Entity(
    tableName = "training_record",
    foreignKeys = [ForeignKey(
        entity = ExerciseTypeEntity::class,
        parentColumns = ["id"], childColumns = ["exerciseTypeId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("date"), Index("exerciseTypeId")],
)
data class TrainingRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** LocalDate.toEpochDay().toInt() */
    val date: Int,
    val exerciseTypeId: Long,
    val durationMin: Int,
    /** null = 当时未填体重（规格 6.1） */
    val calories: Double?,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "record_body_part",
    primaryKeys = ["recordId", "bodyPartId"],
    foreignKeys = [
        ForeignKey(TrainingRecordEntity::class, ["id"], ["recordId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(BodyPartEntity::class, ["id"], ["bodyPartId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bodyPartId")],
)
data class RecordBodyPartEntity(val recordId: Long, val bodyPartId: Long)

@Entity(tableName = "daily_note")
data class DailyNoteEntity(@PrimaryKey val date: Int, val text: String, val updatedAt: Long)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 0,
    val gender: String? = null,
    val age: Int? = null,
    val heightCm: Double? = null,
    val weightKg: Double,
    val weeklyGoal: Int = 0,
    val updatedAt: Long,
)

@Entity(tableName = "food_item")
data class FoodItemEntity(
    @PrimaryKey val id: Long, val name: String, val emoji: String,
    val unitLabel: String, val kcalPerUnit: Double,
)

@Entity(tableName = "meta")
data class MetaEntity(@PrimaryKey val key: String, val value: String)
```

- [ ] **Step 4: 写 `StatsRows.kt`（聚合查询的返回行类型）**

```kotlin
package com.fitrecord.app.data.db

data class PerDayRow(val date: Int, val segments: Int, val minutes: Int)
data class PerTypeRow(val typeId: Long, val name: String, val color: Long, val minutes: Int, val segments: Int)
data class PerPartRow(val partId: Long, val name: String, val segments: Int)
data class RecordWithType(
    val id: Long, val date: Int, val exerciseTypeId: Long, val durationMin: Int,
    val calories: Double?, val note: String, val typeName: String, val typeColor: Long, val met: Double,
)
```

- [ ] **Step 5: 写 7 个 DAO**

`dao/TypeDao.kt`：
```kotlin
package com.fitrecord.app.data.db.dao

import androidx.room.*
import com.fitrecord.app.data.db.entity.ExerciseTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TypeDao {
    @Query("SELECT * FROM exercise_type WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<ExerciseTypeEntity>>

    @Query("SELECT * FROM exercise_type ORDER BY sortOrder, id")
    suspend fun all(): List<ExerciseTypeEntity>

    @Query("SELECT * FROM exercise_type WHERE id = :id")
    suspend fun byId(id: Long): ExerciseTypeEntity?

    @Query("SELECT * FROM exercise_type WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): ExerciseTypeEntity?

    @Insert suspend fun insert(entity: ExerciseTypeEntity): Long

    @Query("INSERT INTO exercise_type(name, met, color, isBuiltin, archived, metOverridden, sortOrder, defaultBodyPartIds) " +
           "VALUES(:name, :met, :color, :isBuiltin, 0, 0, :sortOrder, '')")
    suspend fun insertType(name: String, met: Double, color: Long, isBuiltin: Boolean, sortOrder: Int): Long

    @Update suspend fun update(entity: ExerciseTypeEntity)

    @Query("UPDATE exercise_type SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("SELECT COUNT(*) FROM training_record WHERE exerciseTypeId = :id")
    suspend fun usageCount(id: Long): Int
}
```

`dao/TrainingDao.kt`：
```kotlin
package com.fitrecord.app.data.db.dao

import androidx.room.*
import com.fitrecord.app.data.db.RecordWithType
import com.fitrecord.app.data.db.entity.TrainingRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingDao {
    @Insert suspend fun insert(entity: TrainingRecordEntity): Long
    @Update suspend fun update(entity: TrainingRecordEntity)
    @Query("DELETE FROM training_record WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("DELETE FROM training_record") suspend fun deleteAll()
    @Query("SELECT * FROM training_record WHERE id = :id") suspend fun byId(id: Long): TrainingRecordEntity?

    @Transaction
    @Query("""SELECT r.id, r.date, r.exerciseTypeId, r.durationMin, r.calories, r.note,
                     t.name AS typeName, t.color AS typeColor, t.met
              FROM training_record r JOIN exercise_type t ON t.id = r.exerciseTypeId
              WHERE r.date BETWEEN :from AND :from""")
    fun observeDay(from: Int): Flow<List<RecordWithType>>

    @Transaction
    @Query("""SELECT r.id, r.date, r.exerciseTypeId, r.durationMin, r.calories, r.note,
                     t.name AS typeName, t.color AS typeColor, t.met
              FROM training_record r JOIN exercise_type t ON t.id = r.exerciseTypeId
              WHERE r.date BETWEEN :from AND :to ORDER BY r.date, r.id""")
    fun observeRange(from: Int, to: Int): Flow<List<RecordWithType>>

    @Transaction
    @Query("""SELECT r.id, r.date, r.exerciseTypeId, r.durationMin, r.calories, r.note,
                     t.name AS typeName, t.color AS typeColor, t.met
              FROM training_record r JOIN exercise_type t ON t.id = r.exerciseTypeId
              WHERE r.date BETWEEN :from AND :to""")
    suspend fun rangeOnce(from: Int, to: Int): List<RecordWithType>

    @Query("""SELECT bp.id FROM record_body_part rb JOIN body_part bp ON bp.id = rb.bodyPartId
              WHERE rb.recordId = :recordId ORDER BY bp.sortOrder""")
    suspend fun bodyPartIds(recordId: Long): List<Long>

    @Query("DELETE FROM record_body_part WHERE recordId = :recordId")
    suspend fun clearBodyParts(recordId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkBodyParts(links: List<com.fitrecord.app.data.db.entity.RecordBodyPartEntity>)
}
```

`dao/StatsDao.kt`：
```kotlin
package com.fitrecord.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.fitrecord.app.data.db.PerDayRow
import com.fitrecord.app.data.db.PerPartRow
import com.fitrecord.app.data.db.PerTypeRow

@Dao
interface StatsDao {
    @Query("SELECT COUNT(*) FROM training_record WHERE date BETWEEN :f AND :t")
    suspend fun segments(f: Int, t: Int): Int

    @Query("SELECT COALESCE(SUM(durationMin),0) FROM training_record WHERE date BETWEEN :f AND :t")
    suspend fun minutes(f: Int, t: Int): Int

    @Query("SELECT SUM(calories) FROM training_record WHERE date BETWEEN :f AND :t")
    suspend fun calories(f: Int, t: Int): Double?

    @Query("SELECT COUNT(DISTINCT date) FROM training_record WHERE date BETWEEN :f AND :t")
    suspend fun trainedDays(f: Int, t: Int): Int

    @Query("SELECT COUNT(*) FROM training_record WHERE calories IS NULL AND date BETWEEN :f AND :t")
    suspend fun uncalculated(f: Int, t: Int): Int

    @Query("SELECT date, COUNT(*) AS segments, SUM(durationMin) AS minutes FROM training_record " +
           "WHERE date BETWEEN :f AND :t GROUP BY date ORDER BY date")
    suspend fun perDay(f: Int, t: Int): List<PerDayRow>

    @Query("""SELECT t.id AS typeId, t.name, t.color, SUM(r.durationMin) AS minutes, COUNT(*) AS segments
              FROM training_record r JOIN exercise_type t ON t.id = r.exerciseTypeId
              WHERE r.date BETWEEN :f AND :t GROUP BY t.id ORDER BY minutes DESC, segments DESC""")
    suspend fun perType(f: Int, t: Int): List<PerTypeRow>

    @Query("""SELECT bp.id AS partId, bp.name, COUNT(*) AS segments
              FROM record_body_part rb JOIN training_record r ON r.id = rb.recordId
              JOIN body_part bp ON bp.id = rb.bodyPartId
              WHERE r.date BETWEEN :f AND :t GROUP BY bp.id ORDER BY segments DESC""")
    suspend fun perPart(f: Int, t: Int): List<PerPartRow>

    @Query("SELECT MIN(date) FROM training_record") suspend fun oldestDay(): Int?
}
```

`dao/NoteDao.kt` / `ProfileDao.kt` / `FoodDao.kt` / `MetaDao.kt`：
```kotlin
@Dao interface NoteDao {
    @Query("SELECT * FROM daily_note WHERE date = :date") suspend fun note(date: Int): com.fitrecord.app.data.db.entity.DailyNoteEntity?
    @Query("SELECT * FROM daily_note WHERE date = :date") fun observeNote(date: Int): kotlinx.coroutines.flow.Flow<com.fitrecord.app.data.db.entity.DailyNoteEntity?>
    @Upsert suspend fun upsert(entity: com.fitrecord.app.data.db.entity.DailyNoteEntity)
    @Query("SELECT * FROM daily_note") suspend fun all(): List<com.fitrecord.app.data.db.entity.DailyNoteEntity>
}

@Dao interface ProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 0") suspend fun profile(): com.fitrecord.app.data.db.entity.UserProfileEntity?
    @Query("SELECT * FROM user_profile WHERE id = 0") fun observeProfile(): kotlinx.coroutines.flow.Flow<com.fitrecord.app.data.db.entity.UserProfileEntity?>
    @Upsert suspend fun upsert(entity: com.fitrecord.app.data.db.entity.UserProfileEntity)
}

@Dao interface FoodDao {
    @Query("SELECT * FROM food_item ORDER BY id") suspend fun all(): List<com.fitrecord.app.data.db.entity.FoodItemEntity>
    @Query("SELECT * FROM food_item ORDER BY id") fun observeAll(): kotlinx.coroutines.flow.Flow<List<com.fitrecord.app.data.db.entity.FoodItemEntity>>
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<com.fitrecord.app.data.db.entity.FoodItemEntity>)
}

@Dao interface MetaDao {
    @Query("SELECT value FROM meta WHERE key = :key") suspend fun get(key: String): String?
    @Upsert suspend fun put(entity: com.fitrecord.app.data.db.entity.MetaEntity)
}
```
> 上面四个 DAO 分别写入 `dao/NoteDao.kt`、`dao/ProfileDao.kt`、`dao/FoodDao.kt`、`dao/MetaDao.kt` 四个文件，
> 落地时把全限定名改成正常 `import`（`com.fitrecord.app.data.db.entity.*`、`kotlinx.coroutines.flow.Flow`、`androidx.room.*`）。
> `ProfileDao` 额外需要一行（被 `TrainingRepositoryTest` 用于验证"无体重"分支）：
> ```kotlin
> @Query("DELETE FROM user_profile") suspend fun delete()
> ```
> `NoteDao` 额外需要（被 `saveDayNote` 用于清空当日备注）：
> ```kotlin
> @Query("DELETE FROM daily_note WHERE date = :date") suspend fun delete(date: Int)
> ```

- [ ] **Step 6: 写 `FitRecordDatabase.kt`（含种子回调，本任务先建表，种子在 Task 6 填）**

```kotlin
package com.fitrecord.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitrecord.app.data.db.dao.*
import com.fitrecord.app.data.db.entity.*

@Database(
    entities = [
        ExerciseTypeEntity::class, BodyPartEntity::class, TrainingRecordEntity::class,
        RecordBodyPartEntity::class, DailyNoteEntity::class, UserProfileEntity::class,
        FoodItemEntity::class, MetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class FitRecordDatabase : RoomDatabase() {
    abstract fun typeDao(): TypeDao
    abstract fun trainingDao(): TrainingDao
    abstract fun statsDao(): StatsDao
    abstract fun noteDao(): NoteDao
    abstract fun profileDao(): ProfileDao
    abstract fun foodDao(): FoodDao
    abstract fun metaDao(): MetaDao

    companion object {
        const val DB_NAME = "fitrecord.db"

        val SEED_CALLBACK = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                db.execSQL("INSERT OR REPLACE INTO meta(key, value) VALUES('schema_created_at', strftime('%s','now'))")
            }
        }

        fun build(context: Context): FitRecordDatabase =
            Room.databaseBuilder(context.applicationContext, FitRecordDatabase::class.java, DB_NAME)
                .addCallback(SEED_CALLBACK)
                .build()   // 明确不调用 fallbackToDestructiveMigration（Global Constraints）
    }
}
```

- [ ] **Step 7: 写 `AppContainer.kt` 与 `LocalContainer.kt`，接进 Application**

`AppContainer.kt`：
```kotlin
package com.fitrecord.app

import android.content.Context
import com.fitrecord.app.data.db.FitRecordDatabase

class AppContainer(appContext: Context) {
    private val database: FitRecordDatabase by lazy { FitRecordDatabase.build(appContext) }
    val typeDao get() = database.typeDao()
    val trainingDao get() = database.trainingDao()
    val statsDao get() = database.statsDao()
    val noteDao get() = database.noteDao()
    val profileDao get() = database.profileDao()
    val foodDao get() = database.foodDao()
    val metaDao get() = database.metaDao()
}
```

`LocalContainer.kt`：
```kotlin
package com.fitrecord.app

import androidx.compose.runtime.compositionLocalOf

val LocalContainer = compositionLocalOf<AppContainer> { error("LocalContainer 未提供") }
```

`FitRecordApp.kt`：
```kotlin
package com.fitrecord.app

import android.app.Application

class FitRecordApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

`MainActivity.kt` 的 `setContent` 外层包一层 Provider：
```kotlin
setContent {
    val container = (application as FitRecordApp).container
    CompositionLocalProvider(LocalContainer provides container) {
        FitRecordTheme { /* 现有内容 */ }
    }
}
```

- [ ] **Step 8: 跑测试确认通过 + schema 已导出**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*DatabaseSmokeTest"`
Expected: 2 个测试通过；`app/schemas/com.fitrecord.app.data.db.FitRecordDatabase/1.json` 生成，且其中 `entities` 数组含 8 个实体。

- [ ] **Step 9: Commit**

```powershell
git add android/app/src android/app/schemas
git commit -m "feat(m1): room schema v1 (8 tables), daos, AppContainer"
```

### Task 6: 内置种子数据（运动库 / 部位 / 食物 / MET 参考表）

**Files:**
- Create: `data/db/seed/BuiltinExercises.kt`、`BodyParts.kt`、`Foods.kt`、`MetReference.kt`
- Create: `data/repo/SeedRepository.kt`
- Modify: `AppContainer.kt`（新增 `seedRepo`、`appScope`）
- Test: `data/db/seed/SeedDataTest.kt`、`data/repo/SeedRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 5 的 DAO 与实体
- Produces:
  - `BuiltinExercises.items: List<BuiltinExercise(name, met, colorIndex, defaultBodyPartIds)>`（8 条，顺序即规格 2.1）
  - `BodyParts.items: List<Pair<Long,String>>`（id 1..8：胸 背 肩 手臂 核心 臀 腿 全身有氧）
  - `Foods.items: List<FoodItemEntity>`（10 条，规格 4.4）
  - `MetReference.items: List<MetRef(name, met, intensity)>`（32 条，规格 4.5）
  - `suspend fun SeedRepository.ensureSeeded()`（幂等）

- [ ] **Step 1: 写失败测试 `SeedDataTest.kt`（纯 JVM，不需要 Robolectric）**

```kotlin
package com.fitrecord.app.data.db.seed

import org.junit.Assert.*
import org.junit.Test

class SeedDataTest {
    @Test fun `8 builtin exercises with spec met values`() {
        assertEquals(8, BuiltinExercises.items.size)
        assertEquals(7.0, BuiltinExercises.byName("划船机").met, 1e-9)
        assertEquals(2.5, BuiltinExercises.byName("瑜伽").met, 1e-9)
        assertEquals(8.3, BuiltinExercises.byName("跑步机").met, 1e-9)
    }
    @Test fun `builtin colors follow palette index order`() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7), BuiltinExercises.items.map { it.colorIndex })
    }
    @Test fun `8 body parts with ids 1..8`() {
        assertEquals(8, BodyParts.items.size)
        assertEquals(1L..8L, BodyParts.items.map { it.first })
        assertEquals("全身有氧", BodyParts.items.last().second)
    }
    @Test fun `food library matches spec 4_4 kcal values`() {
        assertEquals(10, Foods.items.size)
        assertEquals(232.0, Foods.byName("米饭").kcalPerUnit, 1e-9)
        assertEquals(350.0, Foods.byName("奶茶").kcalPerUnit, 1e-9)
        assertTrue(Foods.items.all { it.emoji.isNotBlank() && it.unitLabel.isNotBlank() })
    }
    @Test fun `met reference has 32 entries sorted by met and no duplicate name`() {
        assertEquals(32, MetReference.items.size)
        assertEquals(32, MetReference.items.map { it.name }.distinct().size)
        assertTrue(MetReference.items.zipWithNext().all { (a, b) -> a.met <= b.met })
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*SeedDataTest"` → Expected: 编译失败（未定义）

- [ ] **Step 2: 写四个 seed 文件**

`BuiltinExercises.kt`（`colorIndex` 对应 `ExercisePalette.colors` 下标；`defaultBodyPartIds` 用 Task 6 Step 1 的 BodyParts id）：
```kotlin
package com.fitrecord.app.data.db.seed

data class BuiltinExercise(val name: String, val met: Double, val colorIndex: Int, val defaultBodyPartIds: String)

object BuiltinExercises {
    val items = listOf(
        BuiltinExercise("划船机",  7.0, 0, "2,5"),
        BuiltinExercise("椭圆机",  5.0, 1, "7,8"),
        BuiltinExercise("动感单车", 6.8, 2, "7,8"),
        BuiltinExercise("跑步机",  8.3, 3, "8"),
        BuiltinExercise("自由力量", 5.0, 4, "1,2,3,4,5"),
        BuiltinExercise("瑜伽",    2.5, 5, "5"),
        BuiltinExercise("快走",    4.3, 6, "8"),
        BuiltinExercise("游泳",    5.8, 7, "8"),
    )
    fun byName(name: String): BuiltinExercise = items.first { it.name == name }
}
```

`BodyParts.kt`：
```kotlin
package com.fitrecord.app.data.db.seed

object BodyParts {
    val items: List<Pair<Long, String>> = listOf(
        1L to "胸", 2L to "背", 3L to "肩", 4L to "手臂",
        5L to "核心", 6L to "臀", 7L to "腿", 8L to "全身有氧",
    )
    fun name(id: Long): String = items.first { it.first == id }.second
}
```

`Foods.kt`：
```kotlin
package com.fitrecord.app.data.db.seed

import com.fitrecord.app.data.db.entity.FoodItemEntity

object Foods {
    val items = listOf(
        FoodItemEntity(1, "米饭", "🍚", "碗米饭", 232.0),
        FoodItemEntity(2, "香蕉", "🍌", "根香蕉", 106.0),
        FoodItemEntity(3, "苹果", "🍎", "个苹果", 104.0),
        FoodItemEntity(4, "可乐", "🥤", "罐可乐", 139.0),
        FoodItemEntity(5, "巧克力", "🍫", "块巧克力", 55.0),
        FoodItemEntity(6, "鸡腿", "🍗", "个鸡腿", 181.0),
        FoodItemEntity(7, "鸡蛋", "🥚", "个鸡蛋", 72.0),
        FoodItemEntity(8, "吐司", "🍞", "片吐司", 79.0),
        FoodItemEntity(9, "牛奶", "🥛", "盒牛奶", 135.0),
        FoodItemEntity(10, "奶茶", "🧋", "杯奶茶", 350.0),
    )
    fun byName(name: String): FoodItemEntity = items.first { it.name == name }
}
```

`MetReference.kt`（P8b 弹层数据源，按 met 升序）：
```kotlin
package com.fitrecord.app.data.db.seed

data class MetRef(val name: String, val met: Double, val intensity: String)

object MetReference {
    private val light = "低强度(<3)"; private val moderate = "中等(3-6)"; private val high = "较高(6-9)"; private val vhigh = "极高(>9)"
    val items: List<MetRef> = listOf(
        MetRef("散步 3.2km/h", 2.5, light),      MetRef("瑜伽", 2.5, light),
        MetRef("太极", 3.0, light),              MetRef("普拉提", 3.0, light),
        MetRef("家务轻体力", 3.5, light),        MetRef("快走 5.6km/h", 4.3, moderate),
        MetRef("骑行 16km/h 休闲", 4.0, moderate), MetRef("椭圆机", 5.0, moderate),
        MetRef("自由力量 中等", 5.0, moderate),  MetRef("羽毛球 休闲", 5.5, moderate),
        MetRef("游泳 休闲", 5.8, moderate),      MetRef("乒乓球", 5.8, moderate),
        MetRef("舞蹈", 5.5, moderate),           MetRef("动感单车 中等", 6.8, high),
        MetRef("划船机 中等", 7.0, high),        MetRef("篮球 休闲", 6.5, moderate),
        MetRef("跑步 8km/h", 8.3, high),         MetRef("游泳 自由泳中等", 8.0, high),
        MetRef("攀岩", 8.0, high),               MetRef("HIIT", 8.0, high),
        MetRef("爬楼梯", 8.8, high),             MetRef("骑行 20km/h", 8.0, high),
        MetRef("跑步 9.7km/h", 10.0, vhigh),     MetRef("跳绳 中速", 11.0, vhigh),
        MetRef("划船机 剧烈", 10.0, vhigh),      MetRef("足球 比赛", 10.0, vhigh),
        MetRef("跑步 11.3km/h", 11.0, vhigh),    MetRef("波比跳", 10.0, vhigh),
        MetRef("战绳", 12.3, vhigh),             MetRef("冲刺跑", 15.0, vhigh),
    ).sortedBy { it.met }
    fun groupByIntensity(): List<Pair<String, List<MetRef>>> =
        items.groupBy { it.intensity }.toSortedMap(compareBy { order ->
            listOf(light, moderate, high, vhigh).indexOf(order)
        }).map { (k, v) -> k to v }
}
```

- [ ] **Step 3: 写 `SeedRepository.kt`（幂等：只在表为空时写入）**

```kotlin
package com.fitrecord.app.data.repo

import com.fitrecord.app.data.db.dao.FoodDao
import com.fitrecord.app.data.db.dao.TypeDao
import com.fitrecord.app.data.db.entity.BodyPartEntity
import com.fitrecord.app.data.db.entity.ExerciseTypeEntity
import com.fitrecord.app.data.db.seed.BodyParts
import com.fitrecord.app.data.db.seed.BuiltinExercises
import com.fitrecord.app.data.db.seed.Foods
import com.fitrecord.app.ui.theme.ExercisePalette

class SeedRepository(
    private val typeDao: TypeDao,
    private val foodDao: FoodDao,
    private val bodyPartDao: BodyPartDao,
) {
    suspend fun ensureSeeded() {
        if (typeDao.all().isEmpty()) {
            BuiltinExercises.items.forEachIndexed { i, it ->
                typeDao.insert(ExerciseTypeEntity(
                    name = it.name, met = it.met, color = ExercisePalette.colorForId(it.colorIndex.toLong()).toRgbLong(),
                    isBuiltin = true, sortOrder = i, defaultBodyPartIds = it.defaultBodyPartIds,
                ))
            }
        }
        if (bodyPartDao.all().isEmpty()) {
            BodyParts.items.forEach { (id, name) -> bodyPartDao.insert(BodyPartEntity(id, name, id.toInt())) }
        }
        if (foodDao.all().isEmpty()) foodDao.insertAll(Foods.items)
    }
}
```
> 需要在 Task 5 的 `TypeDao` 旁新增 `dao/BodyPartDao.kt`：
> ```kotlin
> @Dao interface BodyPartDao {
>     @Query("SELECT * FROM body_part ORDER BY sortOrder") suspend fun all(): List<BodyPartEntity>
>     @Query("SELECT * FROM body_part ORDER BY sortOrder") fun observeAll(): Flow<List<BodyPartEntity>>
>     @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: BodyPartEntity)
> }
> ```
> 并在 `FitRecordDatabase` 加 `abstract fun bodyPartDao(): BodyPartDao`、`AppContainer` 加 `val bodyPartDao get() = database.bodyPartDao()`。

- [ ] **Step 4: 写 `SeedRepositoryTest.kt`（Robolectric，验证幂等）**

```kotlin
package com.fitrecord.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.data.db.seed.BodyParts
import com.fitrecord.app.data.db.seed.BuiltinExercises
import com.fitrecord.app.data.db.seed.Foods
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SeedRepositoryTest {
    @Test fun `ensureSeeded is idempotent`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), FitRecordDatabase::class.java).build()
        val repo = SeedRepository(db.typeDao(), db.foodDao(), db.bodyPartDao())
        repo.ensureSeeded(); repo.ensureSeeded()
        assertEquals(BuiltinExercises.items.size, db.typeDao().all().size)
        assertEquals(BodyParts.items.size, db.bodyPartDao().all().size)
        assertEquals(Foods.items.size, db.foodDao().all().size)
        db.close()
    }
}
```

- [ ] **Step 5: 在 Application 里触发种子**

`AppContainer.kt` 追加：
```kotlin
val seedRepo by lazy { SeedRepository(typeDao, foodDao, bodyPartDao) }
val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
fun seedNow() = appScope.launch { seedRepo.ensureSeeded() }
```
`FitRecordApp.onCreate` 末尾调用 `container.seedNow()`。

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*SeedDataTest" --tests "*SeedRepositoryTest"` → Expected: 全绿

- [ ] **Step 6: Commit**

```powershell
git add android/app/src; git commit -m "feat(m1): builtin exercise/bodypart/food/MET seed data with idempotent seeder"
```

---

### Task 7: 周月区间工具与日历网格构建器

**Files:**
- Create: `core/WeekUtil.kt`、`domain/CalendarBuilder.kt`
- Test: `core/WeekUtilTest.kt`、`domain/CalendarBuilderTest.kt`（纯 JVM）

**Interfaces:**
- Consumes: 无（纯函数）
- Produces:
  - `WeekUtil.weekStart(date: LocalDate, firstDay: DayOfWeek): LocalDate`
  - `WeekUtil.epochRange(start: LocalDate, days: Long): IntRange`
  - `WeekUtil.weekRange(date: LocalDate, firstDay: DayOfWeek): IntRange`
  - `WeekUtil.monthRange(ym: YearMonth): IntRange`
  - `WeekUtil.monthGridStart(ym: YearMonth, firstDay: DayOfWeek): LocalDate`
  - `data class DayCell(date: LocalDate, inMonth: Boolean, isToday: Boolean, isSelected: Boolean, segmentCount: Int, typeColors: List<Long>, showPlusN: Boolean)`
  - `CalendarBuilder.monthGrid(...): List<DayCell>`（42 格）、`weekRow(...): List<DayCell>`（7 格）

- [ ] **Step 1: 写失败测试 `WeekUtilTest.kt`**

```kotlin
package com.fitrecord.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class WeekUtilTest {
    private val oct12 = LocalDate.of(2026, 10, 12)   // 周一

    @Test fun `monday start keeps monday as week start`() {
        assertEquals(oct12, WeekUtil.weekStart(oct12, DayOfWeek.MONDAY))
        assertEquals(oct12, WeekUtil.weekStart(LocalDate.of(2026, 10, 18), DayOfWeek.MONDAY))
    }
    @Test fun `sunday start shifts back to sunday`() {
        assertEquals(LocalDate.of(2026, 10, 11), WeekUtil.weekStart(oct12, DayOfWeek.SUNDAY))
    }
    @Test fun `week range spans 7 epoch days`() {
        val r = WeekUtil.weekRange(oct12, DayOfWeek.MONDAY)
        assertEquals(7, r.last - r.first + 1)
        assertEquals(oct12.toEpochDay().toInt(), r.first)
    }
    @Test fun `month range for october 2026 covers 1..31`() {
        val r = WeekUtil.monthRange(YearMonth.of(2026, 10))
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay().toInt(), r.first)
        assertEquals(LocalDate.of(2026, 10, 31).toEpochDay().toInt(), r.last)
    }
    @Test fun `month grid starts on previous monday before the 1st`() {
        // 2026-10-01 是周四，周一起始时网格应从 09-28 开始
        assertEquals(LocalDate.of(2026, 9, 28), WeekUtil.monthGridStart(YearMonth.of(2026, 10), DayOfWeek.MONDAY))
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*WeekUtilTest"` → Expected: 编译失败

- [ ] **Step 2: 实现 `core/WeekUtil.kt`**

```kotlin
package com.fitrecord.app.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object WeekUtil {
    fun weekStart(date: LocalDate, firstDay: DayOfWeek): LocalDate =
        date.minusDays(((date.dayOfWeek.value - firstDay.value + 7) % 7).toLong())

    fun epochRange(start: LocalDate, days: Long): IntRange =
        IntRange(start.toEpochDay().toInt(), start.plusDays(days - 1).toEpochDay().toInt())

    fun weekRange(date: LocalDate, firstDay: DayOfWeek): IntRange =
        epochRange(weekStart(date, firstDay), 7)

    fun monthRange(ym: YearMonth): IntRange = epochRange(ym.atDay(1), ym.lengthOfMonth().toLong())

    fun monthGridStart(ym: YearMonth, firstDay: DayOfWeek): LocalDate =
        weekStart(ym.atDay(1), firstDay)

    fun monthsBetween(a: YearMonth, b: YearMonth): Int =
        ChronoUnit.MONTHS.between(a.atDay(1), b.atDay(1)).toInt()
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*WeekUtilTest"` → Expected: 5 个测试通过

- [ ] **Step 3: 写失败测试 `CalendarBuilderTest.kt`**

```kotlin
package com.fitrecord.app.domain

import com.fitrecord.app.data.db.RecordWithType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarBuilderTest {
    private fun rec(day: LocalDate, typeId: Long, color: Long) = RecordWithType(
        id = typeId * 100 + day.toEpochDay(), date = day.toEpochDay().toInt(), exerciseTypeId = typeId,
        durationMin = 30, calories = 100.0, note = "", typeName = "t$typeId", typeColor = color, met = 5.0)

    private val today = LocalDate.of(2026, 10, 12)
    private val oct = YearMonth.of(2026, 10)

    @Test fun `month grid always has 42 cells starting before the 1st`() {
        val cells = CalendarBuilder.monthGrid(oct, DayOfWeek.MONDAY, emptyMap(), today, null)
        assertEquals(42, cells.size)
        assertEquals(LocalDate.of(2026, 9, 28), cells.first().date)
        assertEquals(6, cells.count { it.inMonth } / 7 + if (cells.count { it.inMonth } % 7 == 0) 0 else 0)
    }
    @Test fun `day with records gets distinct colors capped at 3 and plusN marker`() {
        val d = LocalDate.of(2026, 10, 5)
        val byDay = mapOf(d.toEpochDay().toInt() to listOf(rec(d, 1, 0xFF111111L), rec(d, 2, 0xFF222222L),
            rec(d, 3, 0xFF333333L), rec(d, 4, 0xFF444444L)))
        val cell = CalendarBuilder.monthGrid(oct, DayOfWeek.MONDAY, byDay, today, null).first { it.date == d }
        assertEquals(4, cell.segmentCount)
        assertEquals(3, cell.typeColors.size)
        assertTrue(cell.showPlusN)
    }
    @Test fun `same type twice yields one color but two segments`() {
        val d = LocalDate.of(2026, 10, 6)
        val byDay = mapOf(d.toEpochDay().toInt() to listOf(rec(d, 1, 0xFF111111L), rec(d, 1, 0xFF111111L)))
        val cell = CalendarBuilder.monthGrid(oct, DayOfWeek.MONDAY, byDay, today, null).first { it.date == d }
        assertEquals(2, cell.segmentCount); assertEquals(1, cell.typeColors.size)
    }
    @Test fun `today and selected flags are independent`() {
        val byDay = mapOf(today.toEpochDay().toInt() to listOf(rec(today, 1, 0xFFAA0000L)))
        val sel = LocalDate.of(2026, 10, 20)
        val cells = CalendarBuilder.monthGrid(oct, DayOfWeek.MONDAY, byDay, today, sel)
        val todayCell = cells.first { it.date == today }
        assertTrue(todayCell.isToday); assertTrue(!todayCell.isSelected); assertEquals(1, todayCell.segmentCount)
        assertEquals(0, cells.first { it.date == sel }.segmentCount)
        assertTrue(cells.first { it.date == sel }.isSelected)
    }
    @Test fun `week row returns exactly the 7 days of that week`() {
        val row = CalendarBuilder.weekRow(LocalDate.of(2026, 10, 12), DayOfWeek.MONDAY, emptyMap(), today, null)
        assertEquals(7, row.size)
        assertEquals(LocalDate.of(2026, 10, 12), row.first().date)
        assertEquals(LocalDate.of(2026, 10, 18), row.last().date)
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*CalendarBuilderTest"` → Expected: 编译失败

- [ ] **Step 4: 实现 `domain/CalendarBuilder.kt`**

```kotlin
package com.fitrecord.app.domain

import com.fitrecord.app.core.WeekUtil
import com.fitrecord.app.data.db.RecordWithType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class DayCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val segmentCount: Int,
    val typeColors: List<Long>,
    val showPlusN: Boolean,
) {
    val epochDay: Int get() = date.toEpochDay().toInt()
    val trained: Boolean get() = segmentCount > 0
}

object CalendarBuilder {
    private const val GRID_CELLS = 42
    private const val MAX_DOTS = 3

    fun monthGrid(
        month: YearMonth, firstDay: DayOfWeek, recordsByDay: Map<Int, List<RecordWithType>>,
        today: LocalDate, selected: LocalDate?,
    ): List<DayCell> = grid(monthGridStartDays(month, firstDay), month, recordsByDay, today, selected)

    fun weekRow(
        date: LocalDate, firstDay: DayOfWeek, recordsByDay: Map<Int, List<RecordWithType>>,
        today: LocalDate, selected: LocalDate?,
    ): List<DayCell> = grid(
        start = WeekUtil.weekStart(date, firstDay).atStartOfMonth().let { WeekUtil.weekStart(date, firstDay) },
        month = null, recordsByDay = recordsByDay, today = today, selected = selected, count = 7,
    )

    private fun monthGridStartDays(month: YearMonth, firstDay: DayOfWeek): LocalDate =
        WeekUtil.monthGridStart(month, firstDay)

    private fun grid(start: LocalDate, month: YearMonth?, recordsByDay: Map<Int, List<RecordWithType>>,
                     today: LocalDate, selected: LocalDate?, count: Int = GRID_CELLS): List<DayCell> =
        (0 until count).map { i ->
            val d = start.plusDays(i.toLong())
            val recs = recordsByDay[d.toEpochDay().toInt()].orEmpty()
            DayCell(
                date = d,
                inMonth = month?.let { YearMonth.from(d) == it } ?: true,
                isToday = d == today,
                isSelected = selected != null && d == selected,
                segmentCount = recs.size,
                typeColors = recs.map { it.typeColor }.distinct().take(MAX_DOTS),
                showPlusN = recs.map { it.typeColor }.distinct().size > MAX_DOTS,
            )
        }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*CalendarBuilderTest"` → Expected: 5 个测试通过

- [ ] **Step 5: Commit**

```powershell
git add android/app/src; git commit -m "feat(m1): WeekUtil + CalendarBuilder (42-cell month grid, week row, day state)"
```

---

### Task 8: 训练记录仓库（增删改查 + 撤销 + 今日开关）

**Files:**
- Create: `data/repo/TrainingRepository.kt`
- Test: `data/repo/TrainingRepositoryTest.kt`（Robolectric）

**Interfaces:**
- Consumes: Task 5 的 DAO、Task 6 的种子；`CalorieCalculator` 在本任务 Step 2 建立
- Produces:
  - `TrainingRepository.observeDay(date: LocalDate): Flow<List<DayRecord>>`
  - `data class DayRecord(id: Long, date: LocalDate, type: ExerciseTypeEntity, durationMin: Int, calories: Double?, note: String, bodyPartIds: List<Long>)`
  - `suspend fun add(date, exerciseTypeId, durationMin, bodyPartIds, note): Long`
  - `suspend fun update(id: Long, ...): Unit`、`suspend fun delete(id: Long): DayRecord?`（返回被删内容供撤销）
  - `suspend fun restore(record: DayRecord): Long`
  - `suspend fun setTrainedToday(date: LocalDate, trained: Boolean): Int`（返回删除条数）
  - `suspend fun dayRecords(date: LocalDate): List<DayRecord>`

- [ ] **Step 1: 写失败测试 `TrainingRepositoryTest.kt`**

```kotlin
package com.fitrecord.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.data.db.entity.UserProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TrainingRepositoryTest {
    private lateinit var db: FitRecordDatabase
    private lateinit var repo: TrainingRepository
    private val day = LocalDate.of(2026, 10, 12)

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), FitRecordDatabase::class.java).build()
        SeedRepository(db.typeDao(), db.foodDao(), db.bodyPartDao()).ensureSeeded()
        db.profileDao().upsert(UserProfileEntity(weightKg = 70.0, updatedAt = 0))
        repo = TrainingRepository(db.trainingDao(), db.typeDao(), db.profileDao(), db.noteDao())
    }

    @Test fun `add computes calories from met weight and duration`() = runBlocking {
        // 划船机 MET 7.0 × 70kg × 45min/60 = 367.5 → 368
        val id = repo.add(day, exerciseTypeId = 1, durationMin = 45, bodyPartIds = listOf(2, 5), note = "x")
        val r = repo.dayRecords(day).first { it.id == id }
        assertEquals(368.0, r.calories!!, 1e-9)
        assertEquals(listOf(2L, 5L), r.bodyPartIds)
    }

    @Test fun `add without profile stores null calories`() = runBlocking {
        db.profileDao().delete()
        val id = repo.add(day, 1, 30, emptyList(), "")
        assertNull(repo.dayRecords(day).first { it.id == id }.calories)
    }

    @Test fun `segment count is derived from record count not a field`() = runBlocking {
        repo.add(day, 1, 30, emptyList(), ""); repo.add(day, 4, 20, emptyList(), "")
        assertEquals(2, repo.dayRecords(day).size)
    }

    @Test fun `delete returns snapshot and restore reinserts it`() = runBlocking {
        val id = repo.add(day, 1, 45, listOf(2L), "note")
        val snap = repo.delete(id)!!
        assertTrue(repo.dayRecords(day).isEmpty())
        val newId = repo.restore(snap)
        val back = repo.dayRecords(day).first { it.id == newId }
        assertEquals(45, back.durationMin); assertEquals(listOf(2L), back.bodyPartIds); assertEquals("note", back.note)
    }

    @Test fun `setTrainedToday false clears all records of that day`() = runBlocking {
        repo.add(day, 1, 30, emptyList(), ""); repo.add(day, 2, 30, emptyList(), "")
        assertEquals(2, repo.setTrainedToday(day, trained = false))
        assertTrue(repo.dayRecords(day).isEmpty())
    }

    @Test fun `setTrainedToday true creates one default record when empty`() = runBlocking {
        repo.setTrainedToday(day, trained = true)
        assertEquals(1, repo.dayRecords(day).size)
    }

    @Test fun `observeDay emits on insert`() = runBlocking {
        val before = repo.observeDay(day).first().size
        repo.add(day, 1, 60, emptyList(), "")
        assertEquals(before + 1, repo.observeDay(day).first().size)
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*TrainingRepositoryTest"` → Expected: 编译失败

- [ ] **Step 2: 先落 `domain/CalorieCalculator.kt`（被仓库依赖）**

```kotlin
package com.fitrecord.app.domain

import kotlin.math.roundToInt

object CalorieCalculator {
    /** 规格 4.3：kcal = MET × 体重kg × 时长小时，四舍五入到整数 */
    fun kcal(met: Double, weightKg: Double?, durationMin: Int): Double? =
        weightKg?.let { (met * it * durationMin / 60.0).roundToInt().toDouble() }
}
```

- [ ] **Step 3: 实现 `TrainingRepository.kt`**

```kotlin
package com.fitrecord.app.data.repo

import com.fitrecord.app.data.db.RecordWithType
import com.fitrecord.app.data.db.dao.NoteDao
import com.fitrecord.app.data.db.dao.ProfileDao
import com.fitrecord.app.data.db.dao.TrainingDao
import com.fitrecord.app.data.db.dao.TypeDao
import com.fitrecord.app.data.db.entity.ExerciseTypeEntity
import com.fitrecord.app.data.db.entity.RecordBodyPartEntity
import com.fitrecord.app.data.db.entity.TrainingRecordEntity
import com.fitrecord.app.domain.CalorieCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

data class DayRecord(
    val id: Long, val date: LocalDate, val type: ExerciseTypeEntity,
    val durationMin: Int, val calories: Double?, val note: String, val bodyPartIds: List<Long>,
)

class TrainingRepository(
    private val trainingDao: TrainingDao,
    private val typeDao: TypeDao,
    private val profileDao: ProfileDao,
    private val noteDao: NoteDao,
) {
    private suspend fun toDayRecord(r: RecordWithType): DayRecord = DayRecord(
        id = r.id, date = LocalDate.ofEpochDay(r.date.toLong()),
        type = typeDao.byId(r.exerciseTypeId) ?: ExerciseTypeEntity(
            id = r.exerciseTypeId, name = r.typeName, met = r.met, color = r.typeColor, isBuiltin = true),
        durationMin = r.durationMin, calories = r.calories, note = r.note,
        bodyPartIds = trainingDao.bodyPartIds(r.id),
    )

    fun observeDay(date: LocalDate): Flow<List<DayRecord>> {
        val d = date.toEpochDay().toInt()
        return trainingDao.observeRange(d, d).map { list -> list.map { toDayRecord(it) } }
    }

    suspend fun dayRecords(date: LocalDate): List<DayRecord> {
        val d = date.toEpochDay().toInt()
        return trainingDao.rangeOnce(d, d).map { toDayRecord(it) }
    }

    suspend fun add(date: LocalDate, exerciseTypeId: Long, durationMin: Int, bodyPartIds: List<Long>, note: String): Long {
        val now = System.currentTimeMillis()
        val id = trainingDao.insert(newEntity(date, exerciseTypeId, durationMin, bodyPartIds, note, now, now))
        trainingDao.clearBodyParts(id)
        if (bodyPartIds.isNotEmpty())
            trainingDao.linkBodyParts(bodyPartIds.map { RecordBodyPartEntity(id, it) })
        return id
    }

    suspend fun update(id: Long, date: LocalDate, exerciseTypeId: Long, durationMin: Int,
                       bodyPartIds: List<Long>, note: String) {
        val old = trainingDao.byId(id) ?: return
        trainingDao.update(old.copy(date = date.toEpochDay().toInt(), exerciseTypeId = exerciseTypeId,
            durationMin = durationMin, calories = calc(exerciseTypeId, durationMin), note = note,
            updatedAt = System.currentTimeMillis()))
        trainingDao.clearBodyParts(id)
        if (bodyPartIds.isNotEmpty()) trainingDao.linkBodyParts(bodyPartIds.map { RecordBodyPartEntity(id, it) })
    }

    /** 返回快照供 Snackbar 撤销；null 表示记录不存在 */
    suspend fun delete(id: Long): DayRecord? = trainingDao.byId(id)?.let { e ->
        val snap = dayRecords(LocalDate.ofEpochDay(e.date.toLong())).first { it.id == id }
        trainingDao.deleteById(id); snap
    }

    suspend fun restore(record: DayRecord): Long =
        add(record.date, record.type.id, record.durationMin, record.bodyPartIds, record.note)

    /** 规格 P4：开=至少 1 段（默认类型取启用列表首个，60 分钟）；关=清空当日 */
    suspend fun setTrainedToday(date: LocalDate, trained: Boolean): Int {
        val existing = dayRecords(date)
        if (trained) {
            if (existing.isEmpty()) {
                val typeId = defaultTypeId()
                add(date, typeId, durationMin = 60, bodyPartIds = defaultBodyParts(typeId), note = "")
            }
            return existing.size
        }
        existing.forEach { trainingDao.deleteById(it.id) }
        return existing.size
    }

    suspend fun dayNote(date: LocalDate): String = noteDao.note(date.toEpochDay().toInt())?.text ?: ""

    suspend fun saveDayNote(date: LocalDate, text: String) {
        val d = date.toEpochDay().toInt()
        if (text.isBlank()) noteDao.delete(d)
        else noteDao.upsert(DailyNoteEntity(d, text, System.currentTimeMillis()))
    }

    /** 默认打卡类型：启用列表首个（内置划船机 sortOrder=0），保证确定性；
     *  用户"最近使用类型"由 Task 10 的 SettingsStore 记录，只用于 P5 编辑器默认选中 */
    private suspend fun defaultTypeId(): Long =
        typeDao.observeActive().first().firstOrNull()?.id ?: typeDao.all().first().id

    private suspend fun defaultBodyParts(typeId: Long): List<Long> =
        typeDao.byId(typeId)?.defaultBodyPartIds?.split(",")?.mapNotNull { it.toLongOrNull() }.orEmpty()

    private suspend fun calc(typeId: Long, durationMin: Int): Double? =
        CalorieCalculator.kcal(typeDao.byId(typeId)?.met ?: 0.0, profileDao.profile()?.weightKg, durationMin)

    private suspend fun newEntity(date: LocalDate, typeId: Long, durationMin: Int, bodyPartIds: List<Long>,
                                  note: String, createdAt: Long, updatedAt: Long) = TrainingRecordEntity(
        date = date.toEpochDay().toInt(), exerciseTypeId = typeId, durationMin = durationMin,
        calories = calc(typeId, durationMin), note = note, createdAt = createdAt, updatedAt = updatedAt,
    )
}
```
> `saveDayNote` 在 blank 时走 `noteDao.delete(d)`（已在 Step 3 代码中），`NoteDao.delete` 见 Task 5 Step 5 的补充说明。

- [ ] **Step 4: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*TrainingRepositoryTest" --tests "*CalorieCalculatorTest"`
Expected: 全绿（若 `calories` 断言差 1，检查是否用了 `roundToInt` 而非 `toInt` 截断）

- [ ] **Step 5: Commit**

```powershell
git add android/app/src; git commit -m "feat(m1): TrainingRepository CRUD + undo snapshot + today toggle + calorie calc"
```

### Task 9: 食物等价换算（规格 4.4）

**Files:**
- Create: `domain/FoodEquivalent.kt`
- Test: `domain/FoodEquivalentTest.kt`（纯 JVM）

**Interfaces:**
- Consumes: `FoodItemEntity`（Task 5）
- Produces:
  - `data class FoodEquivalent(food: FoodItemEntity, units: Double)`
  - `FoodEquivalent.closest(kcal: Int?, foods: List<FoodItemEntity>, limit: Int = 2): List<FoodEquivalent>`
  - `FoodEquivalent.render(list: List<FoodEquivalent>): String`（`≈ ` 前缀；空入参返回 `""`）
  - `FoodEquivalent.text(kcal: Int?, foods: List<FoodItemEntity>): String`（上面两个的组合）

- [ ] **Step 1: 写失败测试**

```kotlin
package com.fitrecord.app.domain

import com.fitrecord.app.data.db.seed.Foods
import org.junit.Assert.*
import org.junit.Test

class FoodEquivalentTest {
    private val foods = Foods.items

    @Test fun `960 kcal picks rice and chicken drumstick as closest two`() {
        val r = FoodEquivalent.closest(960, foods)
        assertEquals(listOf("米饭", "鸡腿"), r.map { it.food.name })
        assertEquals(4.1, r[0].units, 0.05)   // 960 / 232
        assertEquals(5.3, r[1].units, 0.05)   // 960 / 181
    }
    @Test fun `units are ranked by natural log distance to one`() {
        // 232 kcal 恰好一碗米饭 → 排第一且 units == 1
        val r = FoodEquivalent.closest(232, foods, limit = 3)
        assertEquals("米饭", r.first().food.name)
        assertEquals(1.0, r.first().units, 1e-9)
    }
    @Test fun `tiny burn renders as 少量 not 0_0`() {
        val text = FoodEquivalent.text(5, foods)
        assertTrue(text.contains("少量"))
        assertFalse(text.contains("0.0"))
    }
    @Test fun `null or non positive kcal renders empty`() {
        assertEquals("", FoodEquivalent.text(null, foods))
        assertEquals("", FoodEquivalent.text(0, foods))
        assertEquals("", FoodEquivalent.text(-10, foods))
    }
    @Test fun `render uses middle dot separator and emoji`() {
        val text = FoodEquivalent.text(960, foods)
        assertTrue(text.startsWith("≈ "))
        assertTrue(text.contains("·"))
        assertTrue(text.contains("🍚"))
    }
    @Test fun `limit caps result size and respects zero kcal foods`() {
        assertEquals(1, FoodEquivalent.closest(500, foods, limit = 1).size)
        assertEquals(2, FoodEquivalent.closest(500, foods + foods.first().copy(id = 99, kcalPerUnit = 0.0)).size)
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*FoodEquivalentTest"` → Expected: 编译失败

- [ ] **Step 2: 实现 `domain/FoodEquivalent.kt`**

```kotlin
package com.fitrecord.app.domain

import com.fitrecord.app.data.db.entity.FoodItemEntity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

data class FoodEquivalent(val food: FoodItemEntity, val units: Double)

object FoodEquivalent {
    fun closest(kcal: Int?, foods: List<FoodItemEntity>, limit: Int = 2): List<FoodEquivalent> {
        if (kcal == null || kcal <= 0) return emptyList()
        return foods.filter { it.kcalPerUnit > 0 }
            .map { FoodEquivalent(it, kcal / it.kcalPerUnit) }
            .sortedBy { abs(ln(it.units)) }
            .take(limit)
    }

    fun render(list: List<FoodEquivalent>): String = list.joinToString(" · ") { eq ->
        val n = if (eq.units < 0.1) "少量" else String.format(Locale.CHINA, "%.1f", eq.units)
        "${eq.food.emoji}$n${eq.food.unitLabel}"
    }

    fun text(kcal: Int?, foods: List<FoodItemEntity>): String {
        val list = closest(kcal, foods)
        return if (list.isEmpty()) "" else "≈ " + render(list)
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*FoodEquivalentTest"` → Expected: 6 个测试通过

- [ ] **Step 3: Commit**

```powershell
git add android/app/src; git commit -m "feat(m1): food equivalence ranking and rendering per spec 4.4"
```

---

### Task 10: DataStore 设置（主题模式 / 周起始日 / 最近使用类型）

**Files:**
- Create: `data/prefs/SettingsStore.kt`
- Modify: `AppContainer.kt`（新增 `settings`）
- Test: `data/prefs/SettingsStoreTest.kt`（Robolectric）

**Interfaces:**
- Consumes: Task 3 的 `ThemeMode` 枚举
- Produces:
  - `SettingsStore(ctx: Context)`：`val themeMode: Flow<ThemeMode>`、`suspend fun setThemeMode(m: ThemeMode)`
  - `val firstDayOfWeek: Flow<DayOfWeek>`、`suspend fun setFirstDayOfWeek(d: DayOfWeek)`
  - `val lastExerciseTypeId: Flow<Long?>`、`suspend fun setLastExerciseTypeId(id: Long)`
  - `data class AppSettings(themeMode, firstDayOfWeek, lastExerciseTypeId)` + `SettingsStore.snapshot(): Flow<AppSettings>`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.fitrecord.app.data.prefs

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsStoreTest {
    private fun store(name: String) =
        SettingsStore(ApplicationProvider.getApplicationContext(), testFilePrefix = name)

    @Test fun `defaults are system theme and monday`() = runBlocking {
        val s = store("t1").snapshot().first()
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
        assertEquals(DayOfWeek.MONDAY, s.firstDayOfWeek)
        assertEquals(null, s.lastExerciseTypeId)
    }
    @Test fun `theme mode round trips`() = runBlocking {
        val st = store("t2")
        st.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, st.themeMode.first())
    }
    @Test fun `unknown stored theme string falls back to SYSTEM`() = runBlocking {
        val st = store("t3")
        st.setRaw("theme_mode", "PIXEL")
        assertEquals(ThemeMode.SYSTEM, st.themeMode.first())
    }
    @Test fun `first day of week round trips and persists`() = runBlocking {
        val st = store("t4")
        st.setFirstDayOfWeek(DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, SettingsStore(ApplicationProvider.getApplicationContext(), "t4").firstDayOfWeek.first())
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*SettingsStoreTest"` → Expected: 编译失败

- [ ] **Step 2: 实现 `SettingsStore.kt`**

```kotlin
package com.fitrecord.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fitrecord.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fitrecord_settings")

data class AppSettings(
    val themeMode: ThemeMode, val firstDayOfWeek: DayOfWeek, val lastExerciseTypeId: Long?,
)

class SettingsStore(
    private val context: Context,
    private val testFilePrefix: String? = null,   // 仅测试用：隔离文件
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val FIRST_DAY = stringPreferencesKey("first_day_of_week")
        val LAST_TYPE = longPreferencesKey("last_exercise_type_id")
    }

    private val ds: DataStore<Preferences> get() = context.dataStore

    val themeMode: Flow<ThemeMode> = ds.data.map { ThemeMode.valueOf(it[Keys.THEME] ?: ThemeMode.SYSTEM.name) }
    val firstDayOfWeek: Flow<DayOfWeek> = ds.data.map { runCatching { DayOfWeek.valueOf(it[Keys.FIRST_DAY] ?: DayOfWeek.MONDAY.name) }.getOrDefault(DayOfWeek.MONDAY) }
    val lastExerciseTypeId: Flow<Long?> = ds.data.map { it[Keys.LAST_TYPE] }

    fun snapshot(): Flow<AppSettings> = ds.data.map {
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(it[Keys.THEME] ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM),
            firstDayOfWeek = runCatching { DayOfWeek.valueOf(it[Keys.FIRST_DAY] ?: DayOfWeek.MONDAY.name) }.getOrDefault(DayOfWeek.MONDAY),
            lastExerciseTypeId = it[Keys.LAST_TYPE],
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) { ds.edit { it[Keys.THEME] = mode.name } }
    suspend fun setFirstDayOfWeek(day: DayOfWeek) { ds.edit { it[Keys.FIRST_DAY] = day.name } }
    suspend fun setLastExerciseTypeId(id: Long) { ds.edit { it[Keys.LAST_TYPE] = id } }

    /** 仅测试使用：写入非法值验证容错 */
    suspend fun setRaw(key: String, value: String) { ds.edit { it[stringPreferencesKey(key)] = value } }
}
```
> `testFilePrefix` 在 V1 实现里**不参与** DataStore 文件名（`preferencesDataStore` 是属性委托的单一名）；测试靠 Robolectric 每个测试类独立应据目录。若出现用例互相污染，改为每个测试新建 `PreferenceDataStoreFactory.create(scope = CoroutineScope()) { File(tempDir, "$name.preferences_pb").path }` 的可构造形式，并保留同一接口。

- [ ] **Step 3: 接进 AppContainer 并跑测试**

`AppContainer.kt` 追加：`val settings = SettingsStore(appContext)`

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*SettingsStoreTest"` → Expected: 4 个测试通过

- [ ] **Step 4: Commit**

```powershell
git add android/app/src; git commit -m "feat(m1): DataStore settings (theme mode, first day of week, last used type)"
```

---

### Task 11: 统计仓库（周/月聚合，5 类图表数据）

**Files:**
- Create: `domain/StatsModels.kt`、`data/repo/StatsRepository.kt`
- Test: `data/repo/StatsRepositoryTest.kt`（Robolectric）

**Interfaces:**
- Consumes: `StatsDao`、`WeekUtil`、`FoodEquivalent`、`TrainingRepository`
- Produces:
  - `enum class PeriodType { WEEK, MONTH }`
  - `data class StatsPeriod(type: PeriodType, anchor: LocalDate, range: IntRange, label: String)`
  - `data class SliceStats(name: String, color: Long, minutes: Int, segments: Int, percent: Float)`
  - `data class TrendPoint(label: String, minutes: Int, bucketStart: LocalDate)`
  - `data class StatsSummary(segments: Int, minutes: Int, trainedDays: Int, calories: Int?, uncalculated: Int, avgMinPerDay: Int, avgMinPerSegment: Int)`
  - `data class StatsUiModel(period: StatsPeriod, summary: StatsSummary, perDay: List<PerDayRow>, heat: List<Int>, trend: List<TrendPoint>, byType: List<SliceStats>, byPart: Map<Long, Int>, foodText: String)`
  - `StatsRepository.observeStats(period: StatsPeriod): Flow<StatsUiModel>`
  - `StatsRepository.periodOf(type: PeriodType, anchor: LocalDate, firstDay: DayOfWeek): StatsPeriod`

- [ ] **Step 1: 写失败测试（只测试口径，不测 UI）**

```kotlin
package com.fitrecord.app.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.core.WeekUtil
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.data.db.entity.UserProfileEntity
import com.fitrecord.app.domain.PeriodType
import com.fitrecord.app.domain.StatsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StatsRepositoryTest {
    private lateinit var db: FitRecordDatabase
    private lateinit var training: TrainingRepository
    private lateinit var stats: StatsRepository
    private val weekAnchor = LocalDate.of(2026, 10, 12)   // 周一
    private val month = LocalDate.of(2026, 10, 12)

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), FitRecordDatabase::class.java).build()
        SeedRepository(db.typeDao(), db.foodDao(), db.bodyPartDao()).ensureSeeded()
        db.profileDao().upsert(UserProfileEntity(weightKg = 70.0, updatedAt = 0))
        training = TrainingRepository(db.trainingDao(), db.typeDao(), db.profileDao(), db.noteDao())
        stats = StatsRepository(db.statsDao(), db.foodDao(), training)
    }

    private suspend fun seed() {
        training.add(weekAnchor, 1, 45, listOf(2, 5), "")            // 划船机 背核心
        training.add(weekAnchor.plusDays(1), 1, 30, listOf(2, 5), "")
        training.add(weekAnchor.plusDays(2), 4, 60, listOf(8), "")    // 跑步机 全身有氧
        training.add(weekAnchor.plusDays(2), 6, 15, listOf(5), "")    // 瑜伽 核心（同日 2 段）
        training.add(LocalDate.of(2026, 9, 30), 1, 20, listOf(2), "") // 上一月/上一周
    }

    @Test fun `week period spans monday to sunday`() {
        val p = stats.periodOf(PeriodType.WEEK, weekAnchor, DayOfWeek.MONDAY)
        assertEquals(7, p.range.last - p.range.first + 1)
        assertEquals(LocalDate.of(2026, 10, 12).toEpochDay().toInt(), p.range.first)
        assertEquals("2026-10-12 ~ 10-18", p.label)
    }
    @Test fun `month period excludes previous month day`() {
        val p = stats.periodOf(PeriodType.MONTH, month, DayOfWeek.MONDAY)
        assertEquals(WeekUtil.monthRange(java.time.YearMonth.of(2026, 10)), p.range)
        assertEquals("2026年10月", p.label)
    }
    @Test fun `summary counts segments minutes and trained days`() = runBlocking {
        seed()
        val ui = stats.observeStats(stats.periodOf(PeriodType.WEEK, weekAnchor, DayOfWeek.MONDAY)).first()
        assertEquals(4, ui.summary.segments)
        assertEquals(150, ui.summary.minutes)
        assertEquals(3, ui.summary.trainedDays)      // 12/13/14 三天
        assertEquals(38, ui.summary.avgMinPerDay)    // 150 / 4 周天数（含 0 的天）= 37.5 → 38
        assertEquals(38, ui.summary.avgMinPerSegment) // 150 / 4
    }
    @Test fun `september record is not counted in october week`() = runBlocking {
        seed()
        val ui = stats.observeStats(stats.periodOf(PeriodType.WEEK, weekAnchor, DayOfWeek.MONDAY)).first()
        assertTrue(ui.perDay.none { it.date == LocalDate.of(2026, 9, 30).toEpochDay().toInt() })
    }
    @Test fun `heatmap has one bucket per day of period`() = runBlocking {
        seed()
        val week = stats.observeStats(stats.periodOf(PeriodType.WEEK, weekAnchor, DayOfWeek.MONDAY)).first()
        assertEquals(7, week.heat.size)
        assertEquals(listOf(1, 1, 2, 0, 0, 0, 0), week.heat)   // 14 日有 2 段
    }
    @Test fun `type share is duration weighted and sums to 100 percent`() = runBlocking {
        seed()
        val ui = stats.observeStats(stats.periodOf(PeriodType.MONTH, month, DayOfWeek.MONDAY)).first()
        assertEquals(120, ui.byType.first().minutes)            // 划船机 45+30+20? 否：10 月内 75
        assertTrue(ui.byType.map { it.percent }.sum() in 99f..101f)
        assertTrue(ui.byType.first().name == "划船机")
    }
    @Test fun `radar counts segments per body part with multi part double counting`() = runBlocking {
        seed()
        val ui = stats.observeStats(stats.periodOf(PeriodType.WEEK, weekAnchor, DayOfWeek.MONDAY)).first()
        assertEquals(2, ui.byPart[2L])   // 背：两条划船机
        assertEquals(2, ui.byPart[5L])   // 核心：划船机 + 瑜伽（多部位各计 1）
        assertEquals(8, ui.byPart.size)  // 8 轴齐全，无数据为 0
    }
    @Test fun `empty period yields empty stats and empty food text`() = runBlocking {
        val ui = stats.observeStats(stats.periodOf(PeriodType.WEEK, LocalDate.of(2026, 1, 5), DayOfWeek.MONDAY)).first()
        assertEquals(0, ui.summary.segments)
        assertEquals("", ui.foodText)
        assertNull(ui.summary.calories)
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*StatsRepositoryTest"` → Expected: 编译失败

- [ ] **Step 2: 实现 `domain/StatsModels.kt`**

```kotlin
package com.fitrecord.app.domain

import com.fitrecord.app.core.WeekUtil
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class PeriodType { WEEK, MONTH }

data class StatsPeriod(
    val type: PeriodType, val anchor: LocalDate, val range: IntRange, val label: String,
    val firstDay: DayOfWeek,
) {
    /** ‹ › 翻页：WEEK 移一周，MONTH 移一月；firstDay 随自身携带，调用方不需再传 */
    fun shifted(delta: Int): StatsPeriod =
        PeriodFactory.of(type, shiftedDate(delta), firstDay)

    private fun shiftedDate(delta: Int): LocalDate = when (type) {
        PeriodType.WEEK -> anchor.plusWeeks(delta.toLong())
        PeriodType.MONTH -> YearMonth.from(anchor).plusMonths(delta.toLong()).atDay(1)
    }

    val isCurrentOrFuture: Boolean get() = range.last >= LocalDate.now().toEpochDay().toInt()
}

data class SliceStats(val name: String, val color: Long, val minutes: Int, val segments: Int, val percent: Float)
data class TrendPoint(val label: String, val minutes: Int, val bucketStart: LocalDate)

data class StatsSummary(
    val segments: Int, val minutes: Int, val trainedDays: Int, val calories: Int?,
    val uncalculated: Int, val avgMinPerDay: Int, val avgMinPerSegment: Int,
)

data class StatsUiModel(
    val period: StatsPeriod,
    val summary: StatsSummary,
    val perDay: List<com.fitrecord.app.data.db.PerDayRow>,
    val heat: List<Int>,
    val trend: List<TrendPoint>,
    val byType: List<SliceStats>,
    val byPart: Map<Long, Int>,
    val foodText: String,
)

object PeriodFactory {
    private val weekFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    fun of(type: PeriodType, anchor: LocalDate, firstDay: DayOfWeek): StatsPeriod = when (type) {
        PeriodType.WEEK -> {
            val s = WeekUtil.weekStart(anchor, firstDay)
            StatsPeriod(type, anchor, WeekUtil.epochRange(s, 7),
                "${s.format(weekFmt)} ~ ${s.plusDays(6).format(DateTimeFormatter.ofPattern("MM-dd"))}", firstDay)
        }
        PeriodType.MONTH -> {
            val ym = YearMonth.from(anchor)
            StatsPeriod(type, anchor, WeekUtil.monthRange(ym), "${ym.year}年${ym.monthValue}月", firstDay)
        }
    }
}
```

- [ ] **Step 3: 实现 `data/repo/StatsRepository.kt`**

```kotlin
package com.fitrecord.app.data.repo

import com.fitrecord.app.core.WeekUtil
import com.fitrecord.app.data.db.PerDayRow
import com.fitrecord.app.data.db.dao.FoodDao
import com.fitrecord.app.data.db.dao.StatsDao
import com.fitrecord.app.domain.FoodEquivalent
import com.fitrecord.app.domain.PeriodFactory
import com.fitrecord.app.domain.PeriodType
import com.fitrecord.app.domain.StatsPeriod
import com.fitrecord.app.domain.StatsSummary
import com.fitrecord.app.domain.StatsUiModel
import com.fitrecord.app.domain.TrendPoint
import com.fitrecord.app.domain.SliceStats
import com.fitrecord.app.domain.WeekBuckets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private const val TREND_BUCKETS_WEEK = 8
private const val TREND_BUCKETS_MONTH = 6
private const val TOP_TYPES = 6
private const val OTHERS = "其他"

class StatsRepository(
    private val statsDao: StatsDao,
    private val foodDao: FoodDao,
    private val training: TrainingRepository,
) {
    fun periodOf(type: PeriodType, anchor: LocalDate, firstDay: DayOfWeek): StatsPeriod =
        PeriodFactory.of(type, anchor, firstDay)

    fun observeStats(period: StatsPeriod): Flow<StatsUiModel> = combine(
        statsDao.observeRangeRaw(period.range.first, period.range.last),
        foodDao.observeAll(),
    ) { rows, foods -> buildUi(period, rows, foods) }

    private suspend fun buildUi(period: StatsPeriod, perDayRows: List<PerDayRow>, foods: List<com.fitrecord.app.data.db.entity.FoodItemEntity>): StatsUiModel {
        val from = period.range.first; val to = period.range.last
        val segments = statsDao.segments(from, to)
        val minutes = statsDao.minutes(from, to)
        val calories = statsDao.calories(from, to)
        val days = (to - from + 1)
        val summary = StatsSummary(
            segments = segments, minutes = minutes, trainedDays = statsDao.trainedDays(from, to),
            calories = calories?.let { Math.round(it).toInt() }, uncalculated = statsDao.uncalculated(from, to),
            avgMinPerDay = if (days == 0) 0 else Math.round(minutes.toDouble() / days).toInt(),
            avgMinPerSegment = if (segments == 0) 0 else Math.round(minutes.toDouble() / segments).toInt(),
        )
        val byDate = perDayRows.associate { it.date to it.segments }
        val heat = (from..to).map { byDate[it] ?: 0 }
        val typeRows = statsDao.perType(from, to)
        val total = typeRows.sumOf { it.minutes }.coerceAtLeast(1)
        val top = typeRows.take(TOP_TYPES).map {
            SliceStats(it.name, it.color, it.minutes, it.segments, it.minutes * 100f / total)
        }
        val rest = typeRows.drop(TOP_TYPES)
        val byType = if (rest.isEmpty()) top else top + SliceStats(OTHERS, 0xFFB6BABDL,
            rest.sumOf { it.minutes }, rest.sumOf { it.segments }, rest.sumOf { it.minutes } * 100f / total)
        val partRows = statsDao.perPart(from, to)
        val byPart = BodyPartAxis.allIds.associateWith { id -> partRows.firstOrNull { it.partId == id }?.segments ?: 0 }
        return StatsUiModel(
            period = period, summary = summary, perDay = perDayRows, heat = heat,
            trend = WeekBuckets.trend(period, statsDao),
            byType = byType, byPart = byPart,
            foodText = FoodEquivalent.text(summary.calories, foods),
        )
    }

    private fun firstDayOfPeriod(period: StatsPeriod): DayOfWeek = period.firstDay   // 保留：P2 图表轴标签需知周起始日
}

/** 部位雷达固定 8 轴（规格 2.5），id 与 BodyParts 一致 */
object BodyPartAxis { val allIds = (1L..8L).toList() }
```

`domain/WeekBuckets.kt`（趋势分桶，纯函数，可单测）：
```kotlin
package com.fitrecord.app.domain

import com.fitrecord.app.core.WeekUtil
import com.fitrecord.app.data.db.PerDayRow
import com.fitrecord.app.data.db.dao.StatsDao
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

object WeekBuckets {
    private val monthFmt = DateTimeFormatter.ofPattern("yy-MM")
    private val dayFmt = DateTimeFormatter.ofPattern("MM-dd")

    suspend fun trend(period: StatsPeriod, statsDao: StatsDao): List<TrendPoint> {
        val end = LocalDate.ofEpochDay(period.range.last.toLong())
        return when (period.type) {
            PeriodType.WEEK -> {
                val lastStart = WeekUtil.weekStart(end, period.firstDay)
                (8 downTo 1).map { back ->
                    val s = lastStart.minusWeeks(back.toLong())
                    val r = WeekUtil.epochRange(s, 7)
                    TrendPoint(s.format(dayFmt), statsDao.minutes(r.first, r.last), s)
                }
            }
            PeriodType.MONTH -> {
                val lastYm = YearMonth.from(end)
                (6 downTo 1).map { back ->
                    val ym = lastYm.minusMonths(back.toLong())
                    val r = WeekUtil.monthRange(ym)
                    TrendPoint(ym.format(monthFmt), statsDao.minutes(r.first, r.last), ym.atDay(1))
                }
            }
        }
    }
}
```
> 上面用到 `StatsDao.observeRangeRaw`（返回 `List<PerDayRow>` 的 Flow）。在 `StatsDao` 补：
> ```kotlin
> @Query("SELECT date, COUNT(*) AS segments, SUM(durationMin) AS minutes FROM training_record " +
>        "WHERE date BETWEEN :f AND :t GROUP BY date ORDER BY date")
> fun observeRangeRaw(f: Int, t: Int): Flow<List<PerDayRow>>
> ```

- [ ] **Step 4: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*StatsRepositoryTest"`
Expected: 8 个测试通过。`avgMinPerDay` 失败时确认分母是**周期天数**（7/当月天数）而不是打卡天数（规格 4.6）。

- [ ] **Step 5: 全量回归 + Commit**

Run: `.\gradlew.bat :app:testDebugUnitTest` → Expected: M1 全部测试绿
```powershell
git add android/app/src; git commit -m "feat(m1): stats repository with week/month aggregation, heatmap, trend buckets, radar axes"
```

---

### Task 12: 迁移脚手架与 schema 守护

**Files:**
- Create: `data/db/Migrations.kt`
- Modify: `data/db/FitRecordDatabase.kt`（`addMigrations`）
- Test: `data/db/SchemaGuardTest.kt`

**Interfaces:**
- Consumes: `app/schemas/…/1.json`
- Produces: `FitRecordDatabase.MIGRATIONS: Array<Migration>`（V1 为空数组）、`MetaKeys.SCHEMA_VERSION`、只读模式开关 `AppContainer.readOnly: StateFlow<Boolean>`

- [ ] **Step 1: 写 schema 守护测试（三个守护，均为"通过型"回归测试）**

```kotlin
package com.fitrecord.app.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.data.db.entity.MetaEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SchemaGuardTest {

    /** 守护 1：导出的 schema JSON 必须存在（防止有人关掉 exportSchema 或忘提交） */
    @Test fun `exported schema json is on the test classpath`() {
        val stream = javaClass.classLoader!!.getResourceAsStream(
            "schemas/${FitRecordDatabase::class.java.name}/1.json")
        assertNotNull("app/schemas/…/1.json 缺失：检查 exportSchema=true 与 sourceSets.test.assets.srcDirs", stream)
    }

    /** 守护 2：重开数据库不丢数据（即未启用破坏式回退） */
    @Test fun `reopening a file database keeps existing rows`() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getDatabasePath(FitRecordDatabase.DB_NAME).delete()
        val db1 = FitRecordDatabase.build(ctx)
        db1.metaDao().put(MetaEntity("probe", "kept"))
        db1.close()
        val db2 = FitRecordDatabase.build(ctx)
        assertEquals("kept", db2.metaDao().get("probe"))
        db2.close()
    }

    /** 守护 3：Room 校验到实体与 schema 不一致时，build() 后的首次查询就会报错 */
    @Test fun `database opens without validation error`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx(), FitRecordDatabase::class.java).build()
        assertEquals(0, db.statsDao().segments(0, Int.MAX_VALUE))
        db.close()
    }

    private fun ctx() = ApplicationProvider.getApplicationContext<android.content.Context>()
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*SchemaGuardTest"` → Expected: 3 个测试通过（若守护 1 失败，把 `app/schemas` 加入 `sourceSets.test.assets` 并重新编译）

- [ ] **Step 2: 写 `Migrations.kt` 并接上（V1 为空数组，但链路必须真实存在）**

```kotlin
package com.fitrecord.app.data.db

import androidx.room.migration.Migration

/**
 * 迁移登记表（规格 4-2）。V1 只有 version 1，因此数组为空。
 * 升版时：在此追加 Migration，并在 FitRecordDatabase 的 @Database(version = n) 同步。
 * 绝不使用 fallbackToDestructiveMigration（Global Constraints）。
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}

/** 写入 meta 表的固定键 */
object MetaKeys {
    const val SCHEMA_VERSION = "schema_version"
    const val LAST_IMPORT = "last_import"
    const val CREATED_AT = "schema_created_at"
}
```
`FitRecordDatabase.companion.build()` 里加 `.addMigrations(*Migrations.ALL)`；`SEED_CALLBACK.onCreate` 里补一行
`db.execSQL("INSERT OR REPLACE INTO meta(key, value) VALUES('schema_version', '1')")`，导入时校验此值与 `BackupFile.schemaVersion` 一致（规格 6.6）。

只读模式（规格 6.9）：`AppContainer.kt` 追加
```kotlin
private val _readOnly = MutableStateFlow(false)
val readOnly: StateFlow<Boolean> = _readOnly.asStateFlow()
fun openDb(): FitRecordDatabase = try {
    database.also { _readOnly.value = false }
} catch (t: Throwable) { _readOnly.value = true; database }
```

- [ ] **Step 3: Commit**

```powershell
git add android/app/src; git commit -m "test(m1): schema guard (exported json present, no destructive fallback) + migration registry"
```

---

## M2 核心 UI：日历与记录

### Task 13: 导航骨架与通用组件库

**Files:**
- Create: `ui/nav/Routes.kt`、`ui/nav/FitRecordNavHost.kt`
- Create: `ui/components/{SegmentedSwitch,EmptyState,ConfirmDialog,InfoBanner,MetricCard,TagSelector,MinuteField,RecordCard}.kt`
- Modify: `MainActivity.kt`
- Test: `ui/nav/NavSmokeTest.kt`、`ui/components/MinuteFieldTest.kt`

**Interfaces:**
- Consumes: `LocalContainer`（Task 5）、`FitRecordTheme`/`Dimens`（Task 3）
- Produces:
  - `object Routes { const val CALENDAR/STATS/PROFILE; fun day(LocalDate): String; fun record(LocalDate, Long = -1L): String; const val BODY/TYPE_LIST; fun typeEdit(Long): String; const val BACKUP/APPEARANCE }`
  - `@Composable fun FitRecordRoot(modifier: Modifier = Modifier)` —— 内含 `rememberNavController` + `Scaffold(bottomBar = NavigationBar)` + `NavHost`（底栏即导航，不再单独拆 `BottomBar` 组件）
  - `@Composable fun SegmentedSwitch(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier)`
  - `@Composable fun MinuteField(value: Int, onValueChange: (Int) -> Unit, error: String?)`（快捷 chip 15/30/45/60/90，步进 ±5）
  - `@Composable fun TagSelector(labels: List<Pair<Long, String>>, selected: Set<Long>, onToggle: (Long) -> Unit)`
  - `@Composable fun MetricCard(title: String, value: String, unit: String, caption: String, accent: Boolean = false)`
  - `@Composable fun EmptyState(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null)`
  - `@Composable fun ConfirmDialog(title: String, message: String, confirmLabel: String, destructive: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit)`
  - `@Composable fun InfoBanner(text: String, actionLabel: String, onAction: () -> Unit, onDismiss: () -> Unit)`
  - `@Composable fun RecordCard(record: DayRecord, onClick: () -> Unit, onDelete: () -> Unit)`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.fitrecord.app.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.ui.theme.FitRecordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MinuteFieldTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `quick chip sets 45 minutes`() {
        var value = 0
        rule.setContent { FitRecordTheme { MinuteField(value = value, onValueChange = { value = it }, error = null) } }
        rule.onNodeWithText("45").performClick()
        assertEquals(45, value)
    }

    @Test fun `step plus adds five minutes`() {
        var value = 30
        rule.setContent { FitRecordTheme { MinuteField(value = value, onValueChange = { value = it }, error = null) } }
        rule.onNodeWithText("+5").performClick()
        assertEquals(35, value)
    }

    @Test fun `error text is shown`() {
        rule.setContent { FitRecordTheme { MinuteField(value = 0, onValueChange = {}, error = "时长需在 1-1440 分钟") } }
        rule.onNodeWithText("时长需在 1-1440 分钟").assertExists()
    }
}
```

```kotlin
package com.fitrecord.app.ui.nav

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.LocalContainer
import com.fitrecord.app.TestContainer
import com.fitrecord.app.ui.theme.FitRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NavSmokeTest {
    @get:Rule val rule = createComposeRule()

    private fun content() = rule.setContent {
        FitRecordTheme {
            androidx.compose.runtime.CompositionLocalProvider(LocalContainer provides TestContainer.create()) {
                val nav = rememberNavController()
                FitRecordNavHost(nav)
            }
        }
    }

    @Test fun `three tabs render and calendar is default`() {
        content()
        rule.onNodeWithText("日历").assertExists()
        rule.onNodeWithText("统计").assertExists()
        rule.onNodeWithText("我的").assertExists()
    }

    @Test fun `tapping stats switches destination`() {
        content()
        rule.onNodeWithText("统计").performClick()
        rule.onNodeWithText("数据统计").assertExists()
    }
}
```
> `TestContainer` 在 Step 2 一并创建（内存 Room + 临时 DataStore），后续所有 UI 测试复用。

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*MinuteFieldTest" --tests "*NavSmokeTest"` → Expected: 编译失败

- [ ] **Step 2: 写 `Routes.kt` 与 `TestContainer.kt`**

`ui/nav/Routes.kt`：
```kotlin
package com.fitrecord.app.ui.nav

import java.time.LocalDate

object Routes {
    const val CALENDAR = "calendar"
    const val STATS = "stats"
    const val PROFILE = "profile"
    const val BODY = "body"
    const val TYPE_LIST = "types"
    const val BACKUP = "backup"
    const val APPEARANCE = "appearance"
    const val ONBOARDING = "onboarding"

    const val ARG_DATE = "date"
    const val ARG_ID = "id"

    fun day(date: LocalDate) = "day/${date.toEpochDay()}"
    fun record(date: LocalDate, id: Long = -1L) = "record?date=${date.toEpochDay()}&id=$id"
    fun typeEdit(id: Long) = "type/edit/$id"

    fun dayOf(path: String): LocalDate =
        LocalDate.ofEpochDay(path.substringAfter("day/").substringBefore("?").toLong())
}

val TAB_ROUTES = listOf(Routes.CALENDAR, Routes.STATS, Routes.PROFILE)
```

`src/test/java/com/fitrecord/app/TestContainer.kt`：
```kotlin
package com.fitrecord.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.data.prefs.SettingsStore
import com.fitrecord.app.data.repo.SeedRepository
import com.fitrecord.app.data.repo.StatsRepository
import com.fitrecord.app.data.repo.TrainingRepository
import kotlinx.coroutines.runBlocking

/** 测试专用容器：内存库 + 已种子数据，不跑真实 Activity */
object TestContainer {
    fun create(): AppContainer {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, FitRecordDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { SeedRepository(db.typeDao(), db.foodDao(), db.bodyPartDao()).ensureSeeded() }
        return AppContainer(ctx, overrideDb = db)
    }
}
```
> 需给 `AppContainer` 增加可选参 `overrideDb: FitRecordDatabase? = null`（`private val database = overrideDb ?: lazy { FitRecordDatabase.build(ctx) }`），并把 `repository` 字段以 `by lazy` 暴露：
> ```kotlin
> val trainingRepo by lazy { TrainingRepository(trainingDao, typeDao, profileDao, noteDao) }
> val statsRepo by lazy { StatsRepository(statsDao, foodDao, trainingRepo) }
> ```

- [ ] **Step 3: 写通用组件（每个组件一个文件，内容按下列签名实现）**

`MinuteField.kt`（完整实现，其余组件同构）：
```kotlin
package com.fitrecord.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitrecord.app.ui.theme.Dimens
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment

private val QUICK = listOf(15, 30, 45, 60, 90)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuteField(value: Int, onValueChange: (Int) -> Unit, error: String?) {
    var text by remember(value) { mutableStateOf(if (value == 0) "" else value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { it.filter { c -> c.isDigit() }.take(4).also { t -> text = t; onValueChange(t.toIntOrNull() ?: 0) } },
        label = { Text("时长（分钟）") },
        isError = error != null,
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onValueChange((value - 5).coerceAtLeast(0)) }, modifier = Modifier.height(Dimens.minTouchTarget)) { Text("−5") }
        OutlinedButton(onClick = { onValueChange((value + 5).coerceAtMost(1440)) }, modifier = Modifier.height(Dimens.minTouchTarget)) { Text("+5") }
        QUICK.forEach { m ->
            FilterChip(selected = value == m, onClick = { onValueChange(m) }, label = { Text(m.toString()) })
        }
    }
}
```

`SegmentedSwitch.kt`：用 `SingleChoiceSegmentedButtonRow`（Material3）实现，选项文字传入、选中项用 `primaryContainer`；高度 32dp、最小触控 48dp（外层 `Modifier.heightIn(min = Dimens.minTouchTarget)`）。

`EmptyState.kt`：`Box(fillMaxSize, Center)` + `caption` 文字 + 可选 `Button(text)`；`text` 为必传（规格 2.4）。

`ConfirmDialog.kt`：`AlertDialog(title, text=message, confirmButton = TextButton(contentColor = if (destructive) error else primary) { Text(confirmLabel) }, dismissButton = TextButton { Text("取消") })`。

`InfoBanner.kt`：`Surface(color = primaryContainer)` + 文字 + `TextButton(actionLabel)` + `IconButton(Icons.Default.Close, onDismiss)`。

`MetricCard.kt`：`Surface(shape = MaterialTheme.shapes.medium)` + `Column(cardPadding)`：`labelSmall`(title) + `Row`(`headlineMedium` 28sp `value` + `bodyMedium` `unit`) + `labelSmall`(caption)；`accent = true` 时 `value` 用 `secondary` 色。

`TagSelector.kt`：`FlowRow`（`androidx.compose.foundation.layout.FlowRow`，需 `OptIn(ExperimentalLayoutApi::class)`）+ `FilterChip`，选中时 `border` 用传入颜色。

`RecordCard.kt`：`Surface(onClick)` + 左侧 6dp 圆点（`record.type.color.toColor()`）+ 类型名 + 右侧 `"${durationMin} 分钟"` + 第二行部位名与 `calories`（null 时显示"待完善体重"）；外层 `SwipeToDismissBox`（Material3）包一层，`background` 为删除图标，`onDismiss = onDelete`。

- [ ] **Step 4: 在 `ui/nav/FitRecordNavHost.kt` 里实现 `FitRecordRoot`（NavHost + 底部 3 Tab）并接入 MainActivity**

```kotlin
package com.fitrecord.app.ui.nav

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fitrecord.app.LocalContainer
import com.fitrecord.app.ui.calendar.CalendarScreen
import com.fitrecord.app.ui.profile.ProfileScreen
import com.fitrecord.app.ui.stats.StatsScreen

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    TabSpec(Routes.CALENDAR, "日历", Icons.Default.DateRange),
    TabSpec(Routes.STATS, "统计", Icons.Default.Assessment),
    TabSpec(Routes.PROFILE, "我的", Icons.Default.Person),
)

@Composable
fun FitRecordRoot(modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    Scaffold(
        bottomBar = {
            if (current in TAB_ROUTES) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = current == t.route,
                            onClick = { nav.navigate(t.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.CALENDAR, modifier = modifier.padding(padding)) {
            composable(Routes.CALENDAR) { CalendarScreen(nav) }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.PROFILE) { ProfileScreen(nav) }
        }
    }
}
```
> 二级路由（`day/{date}`、`record?date={date}&id={id}`、`body`、`types`、`type/edit/{id}`、`backup`、`appearance`、`onboarding`）在 Task 15–18 逐个追加到本 `NavHost`；每次追加时同时给 `TAB_ROUTES` 保持丌变（二级页不显示底栏）。

`MainActivity.kt` 的 `setContent` 最终形态：
```kotlin
setContent {
    val app = application as FitRecordApp
    CompositionLocalProvider(LocalContainer provides app.container) {
        val settings by app.container.settings.snapshot().collectAsState(initial = AppSettings(ThemeMode.SYSTEM, DayOfWeek.MONDAY, null))
        FitRecordTheme(themeMode = settings.themeMode) { FitRecordRoot() }
    }
}
```

- [ ] **Step 5: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*MinuteFieldTest" --tests "*NavSmokeTest"`
Expected: 6 个测试通过（`StatsScreen`/`CalendarScreen` 本任务先建空签名 `@Composable fun StatsScreen()` 返回 `Text("数据统计")`，Task 14/20 再填）

- [ ] **Step 6: Commit**

```powershell
git add android/app/src; git commit -m "feat(m2): nav skeleton with 3 tabs + shared component library"
```

---

### Task 14: 日历首页（P1）

**Files:**
- Create: `ui/calendar/CalendarViewModel.kt`、`ui/calendar/CalendarScreen.kt`、`ui/calendar/MonthPager.kt`、`ui/components/DayCell.kt`
- Modify: `ui/nav/FitRecordNavHost.kt`
- Test: `ui/calendar/CalendarViewModelTest.kt`、`ui/calendar/CalendarScreenTest.kt`

**Interfaces:**
- Consumes: `CalendarBuilder`（T7）、`TrainingRepository.observeDay`（T8）、`StatsDao`、`SettingsStore.firstDayOfWeek`（T10）、`FoodEquivalent`（T9）、`LocalContainer.readOnly`
- Produces:
  - `data class CalendarUi(month: YearMonth, cells: List<DayCell>, isWeekMode: Boolean, selected: LocalDate, summary: MonthSummary, showBanner: Boolean)`
  - `data class MonthSummary(days: Int, segments: Int, minutes: Int, calories: Int?, foodText: String)`
  - `CalendarViewModel(repo, dao, settings, clock: () -> LocalDate)` + `fun factory(c: AppContainer): ViewModelProvider.Factory`
  - `fun onMonthShift(delta: Int)`、`fun onDayClick(date: LocalDate)`、`fun onToggleMode()`、`fun dismissBanner()`

- [ ] **Step 1: 写 ViewModel 失败测试**

```kotlin
package com.fitrecord.app.ui.calendar

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.data.prefs.SettingsStore
import com.fitrecord.app.data.repo.SeedRepository
import com.fitrecord.app.data.repo.TrainingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CalendarViewModelTest {
    private lateinit var vm: CalendarViewModel
    private val today get() = LocalDate.of(2026, 10, 12)

    @Before fun setup() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, FitRecordDatabase::class.java).allowMainThreadQueries().build()
        SeedRepository(db.typeDao(), db.foodDao(), db.bodyPartDao()).ensureSeeded()
        val repo = TrainingRepository(db.trainingDao(), db.typeDao(), db.profileDao(), db.noteDao())
        repo.add(today, 1, 45, listOf(2, 5), "")
        vm = CalendarViewModel(repo, db.statsDao(), SettingsStore(ctx)) { today }
    }

    @Test fun `initial state is current month in month mode`() = runBlocking {
        val s = vm.uiState.first()
        assertEquals(YearMonth.of(2026, 10), s.month)
        assertFalse(s.isWeekMode)
        assertEquals(42, s.cells.size)
        assertTrue(s.selected == today)
    }

    @Test fun `day with record reports one segment and one color`() = runBlocking {
        val cell = vm.uiState.first().cells.first { it.date == today }
        assertEquals(1, cell.segmentCount)
        assertEquals(1, cell.typeColors.size)
        assertTrue(cell.trained)
    }

    @Test fun `shifting month changes cells and summary`() = runBlocking {
        vm.onMonthShift(-1)
        val s = vm.uiState.first()
        assertEquals(YearMonth.of(2026, 9), s.month)
        assertEquals(0, s.summary.days)
        assertEquals(0, s.cells.count { it.trained })
    }

    @Test fun `summary aggregates month totals`() = runBlocking {
        val s = vm.uiState.first()
        assertEquals(1, s.summary.days)
        assertEquals(1, s.summary.segments)
        assertEquals(45, s.summary.minutes)
        assertNull(s.summary.calories)      // 本测试未写 profile，卡路里为 null（规格 6.1）
        assertEquals("", s.summary.foodText)
    }

    @Test fun `summary shows food equivalent once weight is set`() = runBlocking {
        db.profileDao().upsert(UserProfileEntity(weightKg = 70.0, updatedAt = 0))
        val s = vm.uiState.first()
        assertEquals(368, s.summary.calories)          // 7.0 × 70 × 45/60 = 367.5 → 368
        assertTrue(s.summary.foodText.startsWith("≈ "))
    }

    @Test fun `week mode returns 7 cells around selected date`() = runBlocking {
        vm.onToggleMode()
        val s = vm.uiState.first()
        assertTrue(s.isWeekMode)
        assertEquals(7, s.cells.size)
        assertEquals(LocalDate.of(2026, 10, 12), s.cells.first().date)  // 周一起始，today 就是首日
    }

    @Test fun `banner shows only when profile missing`() = runBlocking {
        assertTrue(vm.uiState.first().showBanner)      // 未写 profile
        db.profileDao().upsert(UserProfileEntity(weightKg = 70.0, updatedAt = 0))
        assertFalse(vm.uiState.first().showBanner)
    }

    @Test fun `dismissing banner keeps it hidden even without profile`() = runBlocking {
        vm.dismissBanner()
        assertFalse(vm.uiState.first().showBanner)
    }
}
```
> `db` 在本测试类里是字段（`private lateinit var db: FitRecordDatabase`，在 `@Before` 中赋值），
> 以便测试直接写 profile；`@Before` 主体与 `TrainingRepositoryTest` 一致（内存库 + `ensureSeeded()`），
> 区别是**不**预先写 `UserProfileEntity`，因此 `setup` 里只建库、种子、`repo.add(today, 1, 45, listOf(2, 5), "")`。

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*CalendarViewModelTest"` → Expected: 编译失败

- [ ] **Step 2: 实现 `CalendarViewModel.kt`**

```kotlin
package com.fitrecord.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fitrecord.app.AppContainer
import com.fitrecord.app.core.WeekUtil
import com.fitrecord.app.data.db.dao.ProfileDao
import com.fitrecord.app.data.db.dao.StatsDao
import com.fitrecord.app.data.db.entity.UserProfileEntity
import com.fitrecord.app.data.prefs.SettingsStore
import com.fitrecord.app.domain.CalendarBuilder
import com.fitrecord.app.domain.DayCell
import com.fitrecord.app.data.repo.TrainingRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class MonthSummary(val days: Int, val segments: Int, val minutes: Int, val calories: Int?, val foodText: String)

data class CalendarUi(
    val month: YearMonth, val cells: List<DayCell>, val isWeekMode: Boolean,
    val selected: LocalDate, val summary: MonthSummary, val showBanner: Boolean, val readOnly: Boolean,
)

class CalendarViewModel(
    private val repo: TrainingRepository,
    private val statsDao: StatsDao,
    private val settings: SettingsStore,
    private val clock: () -> LocalDate,
) : ViewModel() {

    private val today get() = clock()
    private val month = MutableStateFlow(YearMonth.from(today))
    private val selected = MutableStateFlow(today)
    private val weekMode = MutableStateFlow(false)
    private val bannerDismissed = MutableStateFlow(false)

    /** 网格查询键：月份/选中日/模式/周起始日 合并为一个对象，并预先算好 epochDay 区间 */
    private data class GridKey(
        val month: YearMonth, val selected: LocalDate, val weekMode: Boolean,
        val firstDay: DayOfWeek, val range: IntRange,
    )

    private val key: StateFlow<GridKey> = combine(
        month, selected, weekMode, settings.firstDayOfWeek,
    ) { m, s, w, f ->
        GridKey(m, s, w, f, if (w) WeekUtil.weekRange(s, f) else WeekUtil.monthRange(m))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialKey())

    private val records = key.flatMapLatest { k -> repo.observeRecords(k.range) }
    private val foods = repo.observeFoods()
    private val weightKg = repo.observeWeightKg()

    val uiState: StateFlow<CalendarUi> = combine(
        key, records, foods, weightKg, bannerDismissed,
    ) { k, recs, fs, kg, dismissed -> buildUi(k, recs, fs, kg, dismissed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyUi())

    private fun initialKey() = YearMonth.from(today).let {
        GridKey(it, today, false, DayOfWeek.MONDAY, WeekUtil.monthRange(it))
    }

    private fun emptyUi() = CalendarUi(YearMonth.from(today), emptyList(), false, today,
        MonthSummary(0, 0, 0, null, ""), showBanner = false, readOnly = false)

    /** 纯函数：不访问数据库，因此可直接单测 */
    private fun buildUi(k: GridKey, recs: List<RecordWithType>, fs: List<FoodItemEntity>,
                        weightKg: Double?, dismissed: Boolean): CalendarUi {
        val byDay = recs.groupBy { it.date }
        val cells = if (k.weekMode) CalendarBuilder.weekRow(k.selected, k.firstDay, byDay, today, k.selected)
                    else CalendarBuilder.monthGrid(k.month, k.firstDay, byDay, today, k.selected)
        val calories = recs.mapNotNull { it.calories }.sum().takeIf { recs.any { r -> r.calories != null } }
        return CalendarUi(
            month = k.month, cells = cells, isWeekMode = k.weekMode, selected = k.selected,
            summary = MonthSummary(
                days = byDay.size, segments = recs.size, minutes = recs.sumOf { it.durationMin },
                calories = calories?.let { Math.round(it).toInt() },
                foodText = FoodEquivalent.text(calories?.let { Math.round(it).toInt() }, fs),
            ),
            showBanner = weightKg == null && !dismissed, readOnly = false,
        )
    }

    fun onMonthShift(delta: Int) { viewModelScope.launch { month.update { it.plusMonths(delta.toLong()) } } }
    fun onDayClick(date: LocalDate) { viewModelScope.launch { selected.value = date } }
    fun onToggleMode() { viewModelScope.launch { weekMode.update { !it } } }
    fun dismissBanner() { viewModelScope.launch { bannerDismissed.value = true } }

    companion object {
        fun factory(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CalendarViewModel(c.trainingRepo, c.statsDao, c.settings) { LocalDate.now() } }
        }
    }
}
```
import 需含：`com.fitrecord.app.data.db.RecordWithType`、`com.fitrecord.app.data.db.entity.FoodItemEntity`、
`com.fitrecord.app.domain.{CalendarBuilder, DayCell, FoodEquivalent}`、`kotlinx.coroutines.flow.*`（含 `flatMapLatest`，已在 coroutines 依赖内）。

**`TrainingRepository` 需补的三个方法**（追加到 Task 8 的实现里，供本任务与 P4 使用）：
```kotlin
/** 供日历/统计按任意区间观察，区间由上层 GridKey 驱动 */
fun observeRecords(range: IntRange): Flow<List<RecordWithType>> =
    trainingDao.observeRange(range.first, range.last)

/** 食物库变化少，直接透传 Room Flow */
fun observeFoods(): Flow<List<FoodItemEntity>> = foodDao.observeAll()

/** 体重为 null 时日历页显示引导 Banner（规格 6.1） */
fun observeWeightKg(): Flow<Double?> = profileDao.observeProfile().map { it?.weightKg }
```
> 这三个方法要 `foodDao`，因此 Task 8 的 `TrainingRepository(...)` 构造参从 4 个变为 **5 个**：
> `(trainingDao, typeDao, profileDao, noteDao, foodDao)`。同步更新 Task 8/15/16 各测试 `@Before` 里的构造调用为
> `TrainingRepository(db.trainingDao(), db.typeDao(), db.profileDao(), db.noteDao(), db.foodDao())`，
> 并在 `AppContainer.trainingRepo` 里传 `database.foodDao()`。

- [ ] **Step 3: 实现 `DayCell.kt` 组件（规格 2.4 / 4.1）**

```kotlin
package com.fitrecord.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fitrecord.app.domain.DayCell
import com.fitrecord.app.ui.theme.Dimens
import com.fitrecord.app.ui.theme.LocalUnreadColor
import com.fitrecord.app.ui.theme.toColor

@Composable
fun DayCellView(cell: DayCell, monthLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val unread = LocalUnreadColor.current
    val desc = "${cell.date.monthValue}月${cell.date.dayOfMonth}日，" +
        if (cell.trained) "已健身${cell.segmentCount}段" else "未健身"
    Box(
        modifier = modifier
            .height(if (monthLabel == "month") Dimens.dayCellHeightMonth else Dimens.dayCellHeightWeek)
            .clip(CircleShape)
            .clickable { onClick() }
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        // 选中/今日圆底
        when {
            cell.isToday -> Box(Modifier.size(Dimens.selectedCircle).background(MaterialTheme.colorScheme.primary, CircleShape))
            cell.isSelected -> Box(Modifier.size(Dimens.selectedCircle)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (cell.trained || cell.isToday) FontWeight.Medium else FontWeight.Normal,
                color = when {
                    cell.isToday -> MaterialTheme.colorScheme.onPrimary
                    !cell.inMonth -> unread.copy(alpha = 0.5f)
                    !cell.trained -> unread
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
            )
            // 农历位：V1 不做，固定占位 10dp（规格 2.3）
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                cell.typeColors.take(3).forEach { c ->
                    Box(Modifier.size(Dimens.dotSize).background(c.toColor(), CircleShape))
                }
                if (cell.showPlusN) Text("+", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
```

- [ ] **Step 4: 实现 `MonthPager.kt` 与 `CalendarScreen.kt`**

关键结构（完整代码落盘时按此展开，不要新增其它状态）：
```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonthPager(cells: List<DayCell>, month: YearMonth, isWeekMode: Boolean, page: Int,
               onPageChange: (Int) -> Unit, onDayClick: (LocalDate) -> Unit) {
    val pager = rememberPagerState(initialPage = CENTER_PAGE, pageCount = { MONTH_WINDOW })   // MONTH_WINDOW = 25
    LaunchedEffect(page) { if (pager.page != page) pager.animateScrollToPage(page) }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { pageIndex ->
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { /* labelSmall 等宽 */ } } // 周日起始时重排
            val shown = if (isWeekMode) cells.takeLast(7) else cells
            shown.chunked(7).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { cell ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            DayCellView(cell = cell, monthLabel = if (isWeekMode) "week" else "month",
                                onClick = { onDayClick(cell.date) })
                        }
                    }
                }
            }
        }
    }
}
```
`CalendarScreen.kt` 顶层：`Scaffold` 内 `Column(Modifier.fillMaxSize().padding(Dimens.pagePadding))`：
1. 标题行：`Text("${month.year}年${month.monthValue}月", style = headlineSmall)` + `IconButton(Icons.Default.KeyboardArrowLeft/Right)`
2. `SegmentedSwitch(listOf("月", "周"), selected = if (ui.isWeekMode) 1 else 0, onSelect = { vm.onToggleMode() })`
3. `MonthPager(...)`（左右滑动 → `vm.onMonthShift(±1)`）
4. `InfoBanner("填写体重后才能计算卡路里", "去设置", onAction = { nav.navigate(Routes.BODY) }, onDismiss = { vm.dismissBanner() })`（仅 `showBanner`）
5. 摘要条 `Surface(onClick = { nav.navigate(Routes.STATS) })`：`"本月 ${d}天 · ${s}段 · ${m}分"` + `"≈ ${calories} kcal  ${foodText}"`
6. `FAB`（`Modifier.align(End + Bottom)`，`contentDescription = "添加训练"`）→ `nav.navigate(Routes.record(ui.selected))`

所有文案从 `strings.xml` 引用（`stringResource(R.string.calendar_summary_month, ...)`），不内联中文字面量到逻辑代码。

- [ ] **Step 5: 写 UI 冒烟测试并跑通**

`ui/calendar/CalendarScreenTest.kt`：
```kotlin
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CalendarScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `today cell is rendered with primary circle and month title exists`() {
        rule.setContent {
            FitRecordTheme {
                CompositionLocalProvider(LocalContainer provides TestContainer.create()) {
                    CalendarScreen(nav = null, vm = CalendarViewModel.preview())
                }
            }
        }
        rule.onNodeWithText("12").assertExists()
        rule.onNodeWithContentDescription("10月12日，已健身1段").assertExists()
    }

    @Test fun `week mode shows 7 cells only`() {
        // 同构：先断言 42 个日期节点 → 切周后只剩 7 个可点击日期
    }
}
```
> 第二个测试落地时写成：`rule.onNodeWithText("18").assertExists()`（周内）与 `rule.onNodeWithText("26").assertDoesNotExist()`（超出周范围）。
> `CalendarViewModel.preview()`：在 VM 伴生对象里用假仓库（`FakeTrainingRepository`）构造，仅测试使用，标 `@VisibleForTesting`。

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*CalendarViewModelTest" --tests "*CalendarScreenTest"` → Expected: 全绿

- [ ] **Step 6: 手工验证（可选，有真机时）**

```powershell
.\gradlew.bat :app:installDebug
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.fitrecord.app/.MainActivity
```
Expected: 手机打开日历首页，今天为绿色实心圆。

- [ ] **Step 7: Commit**

```powershell
git add android/app/src; git commit -m "feat(m2): calendar home (month/week pager, day cells, summary bar, FAB)"
```

---

### Task 15: 当日记录页（P4）

**Files:**
- Create: `ui/day/DayRecordViewModel.kt`、`ui/day/DayRecordScreen.kt`
- Modify: `ui/nav/FitRecordNavHost.kt`（加 `day/{date}`）
- Test: `ui/day/DayRecordViewModelTest.kt`

**Interfaces:**
- Consumes: `TrainingRepository`（T8）、`FoodEquivalent`（T9）、`SettingsStore.lastExerciseTypeId`（T10）
- Produces:
  - `data class DayUi(date: LocalDate, trained: Boolean, records: List<DayRecord>, segments: Int, minutes: Int, calories: Int?, foodText: String, dayNote: String, isToday: Boolean, pendingUndo: DayRecord?)`
  - `DayRecordViewModel(repo, settings, date: LocalDate)`；`fun toggleTrained(on: Boolean)`、`fun requestDelete(id: Long)`、`fun confirmDelete()`、`fun undoDelete()`、`fun saveNote(text: String)`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.fitrecord.app.ui.day

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.data.db.FitRecordDatabase
import com.fitrecord.app.data.db.entity.UserProfileEntity
import com.fitrecord.app.data.prefs.SettingsStore
import com.fitrecord.app.data.repo.SeedRepository
import com.fitrecord.app.data.repo.TrainingRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DayRecordViewModelTest {
    private lateinit var vm: DayRecordViewModel
    private lateinit var repo: TrainingRepository
    private val day = LocalDate.of(2026, 10, 12)

    @Before fun setup() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, FitRecordDatabase::class.java).allowMainThreadQueries().build()
        SeedRepository(db.typeDao(), db.foodDao(), db.bodyPartDao()).ensureSeeded()
        db.profileDao().upsert(UserProfileEntity(weightKg = 70.0, updatedAt = 0))
        repo = TrainingRepository(db.trainingDao(), db.typeDao(), db.profileDao(), db.noteDao())
        vm = DayRecordViewModel(repo, SettingsStore(ctx)) { day }
    }

    @Test fun `empty day is not trained and has zero aggregates`() = runBlocking {
        val s = vm.uiState.first()
        assertFalse(s.trained); assertEquals(0, s.segments); assertNull(s.calories); assertEquals("", s.foodText)
    }

    @Test fun `toggle on creates one 60 minute record`() = runBlocking {
        vm.toggleTrained(true)
        val s = vm.uiState.first()
        assertTrue(s.trained); assertEquals(1, s.segments); assertEquals(60, s.records.first().durationMin)
    }

    @Test fun `aggregates sum minutes and calories`() = runBlocking {
        repo.add(day, 1, 45, listOf(2), ""); repo.add(day, 4, 30, listOf(8), "")
        val s = vm.uiState.first()
        assertEquals(2, s.segments); assertEquals(75, s.minutes)
        assertEquals(368 + 291, s.calories)   // 划船机 7.0×70×0.75=367.5→368；跑步机 8.3×70×0.5=290.5→291
    }

    @Test fun `toggle off clears the day`() = runBlocking {
        vm.toggleTrained(true); vm.toggleTrained(false)
        assertFalse(vm.uiState.first().trained)
    }

    @Test fun `delete keeps snapshot for undo and restores it`() = runBlocking {
        val id = repo.add(day, 1, 45, listOf(2), "")
        vm.requestDelete(id); vm.confirmDelete()
        assertEquals(0, vm.uiState.first().segments)
        vm.undoDelete()
        assertEquals(1, vm.uiState.first().segments)
    }

    @Test fun `day note round trips and blank clears`() = runBlocking {
        vm.saveNote("今天背有点紧")
        assertEquals("今天背有点紧", vm.uiState.first().dayNote)
        vm.saveNote("   ")
        assertEquals("", vm.uiState.first().dayNote)
    }
}
```
> 上面两个期望值的来源：划船机 MET 7.0 × 70kg × 45/60 = 367.5 → **368**；跑步机 MET 8.3 × 70kg × 30/60 = 290.5 → **291**（均用 `roundToInt`，规格 4.3）。
> 若实现用了 `toInt()` 截断会得到 367 + 290，测试会立即失败——这正是本测试的价值。

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*DayRecordViewModelTest"` → Expected: 编译失败

- [ ] **Step 2: 实现 `DayRecordViewModel.kt`**

```kotlin
package com.fitrecord.app.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitrecord.app.data.repo.DayRecord
import com.fitrecord.app.data.repo.TrainingRepository
import com.fitrecord.app.data.prefs.SettingsStore
import com.fitrecord.app.domain.FoodEquivalent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DayUi(
    val date: LocalDate, val trained: Boolean, val records: List<DayRecord>,
    val segments: Int, val minutes: Int, val calories: Int?, val foodText: String,
    val dayNote: String, val isToday: Boolean, val pendingUndo: DayRecord?,
)

class DayRecordViewModel(
    private val repo: TrainingRepository,
    private val settings: SettingsStore,
    clock: () -> LocalDate,
    initialDate: LocalDate,
) : ViewModel() {
    private val date = MutableStateFlow(initialDate)
    private val undo = MutableStateFlow<DayRecord?>(null)
    private val today = clock()

    val uiState: StateFlow<DayUi> = combine(
        repo.observeDayFlow(date), repo.foodFlow(), settings.lastExerciseTypeId
    ) { records, foods, _ ->
        val minutes = records.sumOf { it.durationMin }
        val cal = records.mapNotNull { it.calories }.takeIf { it.isNotEmpty() }?.sum()
        DayUi(
            date = date.value, trained = records.isNotEmpty(), records = records,
            segments = records.size, minutes = minutes, calories = cal?.toInt(),
            foodText = FoodEquivalent.text(cal?.toInt(), foods),
            dayNote = "", isToday = date.value == today, pendingUndo = undo.value,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
        DayUi(initialDate, false, emptyList(), 0, 0, null, "", "", initialDate == today, null))

    fun toggleTrained(on: Boolean) = viewModelScope.launch {
        val count = repo.setTrainedToday(date.value, on)
        if (!on && count > 0) undo.value = null   // 批量清空不进入撤销（需 ConfirmDialog 二次确认）
    }

    fun requestDelete(id: Long) = viewModelScope.launch { undo.value = repo.delete(id) }
    fun confirmDelete() { /* 删除已在 requestDelete 完成，本方法仅关闭确认弹窗 */ undo.value?.let { } }
    fun undoDelete() = viewModelScope.launch { undo.value?.let { repo.restore(it) }; undo.value = null }
    fun saveNote(text: String) = viewModelScope.launch { repo.saveDayNote(date.value, text) }
}
```
> 交互约定（规格 P4）：**先弹 `ConfirmDialog`，用户确认后才调 `requestDelete`**；`requestDelete` 直接删并留快照，5s 内 `undoDelete` 可回。`confirmDelete()` 不删数据，因此实现时把它简化为 `fun dismissUndo()`，并在 `DayRecordScreen` 里用 `LaunchedEffect(ui.pendingUndo) { delay(5000); vm.dismissUndo() }`。
> `dayNote` 需单独一条 flow（`repo.observeDayNote(date)`）参与 `combine`，不要把空字符串当成实现。

- [ ] **Step 3: 实现 `DayRecordScreen.kt`**

结构（对应规格 P4 线框）：`Scaffold(topBar = TopAppBar(title = "${m}月${d}日 ${weekday}${补录后缀}"))` + `LazyColumn(contentPadding = pagePadding)`：
1. `Card`：`Row` 内 `Text("今日已健身")` + `Switch(checked = ui.trained, onCheckedChange = { if (!it) confirmClear() else vm.toggleTrained(true) })`
2. `MetricCardCompact`：`"${ui.segments} 段 · ${ui.minutes} 分钟 · ${ui.calories ?: "—"} kcal"` + 下一行 `ui.foodText`
3. `items(ui.records) { RecordCard(record, onClick = { nav.navigate(Routes.record(ui.date, it.id)) }, onDelete = { confirmDelete(it) }) }`
4. `OutlinedButton(fillMaxWidth)`："＋ 添加一段训练" → `nav.navigate(Routes.record(ui.date))`
5. `OutlinedTextField("备注（当日）", ui.dayNote)`，`onValueChange` 去抖 500ms 后 `vm.saveNote`
6. `SnackbarHost`：`pendingUndo != null` 时 `show("已删除 1 段", actionLabel = "撤销")`，返回 `SnackbarResult.Dismissed` 则 `vm.dismissUndo()`

删除确认：`var pendingDelete by remember { mutableStateOf<DayRecord?>(null) }`，非空时 `ConfirmDialog(title="删除这段训练？", message="${it.type.name} · ${it.durationMin} 分钟", confirmLabel="删除", destructive = true, onConfirm = { vm.requestDelete(it.id) })`。

- [ ] **Step 4: 跑测试 + Commit**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*DayRecordViewModelTest"` → Expected: 6 个测试通过
```powershell
git add android/app/src; git commit -m "feat(m2): day record screen with today toggle, swipe delete + undo, day note"
```

---

### Task 16: 记录编辑页（P5）

**Files:**
- Create: `ui/record/RecordEditorViewModel.kt`、`ui/record/RecordEditorScreen.kt`、`domain/validate/RecordValidator.kt`
- Modify: `ui/nav/FitRecordNavHost.kt`（加 `record?date={date}&id={id}`）
- Test: `domain/validate/RecordValidatorTest.kt`、`ui/record/RecordEditorViewModelTest.kt`

**Interfaces:**
- Consumes: `TrainingRepository.add/update`（T8）、`TypeDao.observeActive`（T5）、`ProfileDao`、`SettingsStore.setLastExerciseTypeId`（T10）、`CalorieCalculator`（T8）
- Produces:
  - `RecordValidator.validate(durationMin: Int, typeSelected: Boolean, note: String): List<FieldError>`，`enum class FieldError { DURATION_RANGE, TYPE_REQUIRED, NOTE_TOO_LONG }`（时长 1–1440；备注 ≤500 字）
  - `data class EditorUi(date: LocalDate, mode: Mode, types: List<ExerciseTypeEntity>, selectedType: Long?, minutes: Int, parts: List<Pair<Long, String>>, selectedParts: Set<Long>, note: String, previewKcal: Int?, weightMissing: Boolean, errors: List<FieldError>, dirty: Boolean)`，`enum class Mode { CREATE, EDIT }`
  - `RecordEditorViewModel`：`fun onType(id: Long)`、`fun onMinutes(v: Int)`、`fun onTogglePart(id: Long)`、`fun onNote(s: String)`、`fun onDate(d: LocalDate)`、`fun save(): Boolean`、`fun canDiscard(): Boolean`

- [ ] **Step 1: 写 `RecordValidatorTest.kt`（纯 JVM）**

```kotlin
package com.fitrecord.app.domain.validate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordValidatorTest {
    private fun errs(min: Int, type: Boolean = true, note: String = "") =
        RecordValidator.validate(min, type, note)

    @Test fun `zero minutes fails range`() {
        assertEquals(listOf(FieldError.DURATION_RANGE), errs(0))
    }
    @Test fun `1441 minutes fails range`() {
        assertTrue(errs(1441).contains(FieldError.DURATION_RANGE))
    }
    @Test fun `one and 1440 minutes pass`() {
        assertTrue(errs(1).isEmpty()); assertTrue(errs(1440).isEmpty())
    }
    @Test fun `missing type fails type required`() {
        assertEquals(listOf(FieldError.TYPE_REQUIRED), errs(30, type = false))
    }
    @Test fun `note over 500 chars fails`() {
        assertTrue(errs(30, note = "x".repeat(501)).contains(FieldError.NOTE_TOO_LONG))
    }
    @Test fun `all errors reported together`() {
        assertEquals(3, errs(0, type = false, note = "x".repeat(600)).size)
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*RecordValidatorTest"` → Expected: 编译失败

- [ ] **Step 2: 实现 `RecordValidator.kt`**

```kotlin
package com.fitrecord.app.domain.validate

enum class FieldError { DURATION_RANGE, TYPE_REQUIRED, NOTE_TOO_LONG }

object RecordValidator {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 1440
    const val MAX_NOTE = 500

    fun validate(durationMin: Int, typeSelected: Boolean, note: String): List<FieldError> = buildList {
        if (durationMin !in MIN_MINUTES..MAX_MINUTES) add(FieldError.DURATION_RANGE)
        if (!typeSelected) add(FieldError.TYPE_REQUIRED)
        if (note.length > MAX_NOTE) add(FieldError.NOTE_TOO_LONG)
    }

    fun message(e: FieldError): String = when (e) {
        FieldError.DURATION_RANGE -> "时长需在 $MIN_MINUTES-$MAX_MINUTES 分钟"
        FieldError.TYPE_REQUIRED -> "请选择运动方式"
        FieldError.NOTE_TOO_LONG -> "备注最多 $MAX_NOTE 字"
    }
}
```

- [ ] **Step 3: 写 ViewModel 测试（关键断言：实时卡路里预览 + 保存后记录最近使用类型）**

```kotlin
package com.fitrecord.app.ui.record

// 准备：内存库 + 种子 + profile(70kg)，构造 RecordEditorViewModel(repo, typeDao, profileDao, settings, date, id = -1L)
@Test fun `preview kcal updates when minutes change`() = runBlocking {
    vm.onType(1L); vm.onMinutes(45)
    assertEquals(368, vm.uiState.value.previewKcal)      // 7.0 × 70 × 0.75 = 367.5 → 368
    vm.onMinutes(90)
    assertEquals(735, vm.uiState.value.previewKcal)      // 7.0 × 70 × 1.5 = 735
}
@Test fun `save is blocked while invalid`() = runBlocking {
    vm.onMinutes(0); assertFalse(vm.save())
    assertEquals(0, repo.dayRecords(date).size)
}
@Test fun `save writes record and marks type as last used`() = runBlocking {
    vm.onType(1L); vm.onMinutes(45); vm.onTogglePart(2L)
    assertTrue(vm.save())
    val r = repo.dayRecords(date).single()
    assertEquals(45, r.durationMin); assertEquals(listOf(2L), r.bodyPartIds); assertEquals(368.0, r.calories!!, 1e-9)
    assertEquals(1L, settings.lastExerciseTypeId.first())
}
@Test fun `dirty flag is false initially and true after edit`() {
    assertFalse(vm.uiState.value.dirty); vm.onMinutes(30); assertTrue(vm.uiState.value.dirty)
}
@Test fun `selecting aerobic type auto selects 全身有氧 part`() = runBlocking {
    vm.onType(8L)   // 游泳，defaultBodyPartIds = "8"
    assertEquals(setOf(8L), vm.uiState.value.selectedParts)
}
```
> 上面 5 个测试完整写进 `RecordEditorViewModelTest.kt`，`@Before` 部分与 `DayRecordViewModelTest` 完全一致（内存库 + `SeedRepository.ensureSeeded()` + `profileDao.upsert(UserProfileEntity(weightKg = 70.0, updatedAt = 0))`）。

- [ ] **Step 4: 实现 ViewModel 与页面**

`RecordEditorViewModel` 要点：
- `private val selectedType = MutableStateFlow<Long?>(null)`、`minutes`、`selectedParts`、`note`、`date` 五个 `MutableStateFlow`
- `uiState: StateFlow<EditorUi>` = `combine` 五个输入 + `typeDao.observeActive()` + `profileDao.observeProfile()` + `bodyPartDao.observeAll()`，每次变化重算 `previewKcal = CalorieCalculator.kcal(met, weightKg, minutes)?.roundToInt()` 与 `errors = RecordValidator.validate(...)`
- `fun onType(id: Long)`：写入类型同时 `selectedParts = 类型的 defaultBodyPartIds`（仅当当前未手动选过部位）
- `suspend fun save(): Boolean`：`errors.isEmpty()` 才写库（`mode == CREATE → add`，否则 `update`），随后 `settings.setLastExerciseTypeId(selectedType)`，返回 true
- `EDIT` 模式初始化：`repo.dayRecords(date).first { it.id == id }` 回填全部字段（不新增仓库方法）

`RecordEditorScreen` 结构（对应规格 P5）：`LazyColumn` 内 5 个 `Card`：日期行（`DatePickerDialog`）、类型行（`LazyRow` + 末尾 `＋自定义` → `nav.navigate(Routes.TYPE_LIST)`）、时长行（`MinuteField`）、部位行（`TagSelector`）、预览行（`Text("预计消耗 ${previewKcal} kcal")` + `caption("MET ${met} × ${weight}kg × ${hours}h")`）+ 备注 `OutlinedTextField`；底栏 `Button("保存", enabled = ui.errors.isEmpty())`；`BackHandler(enabled = ui.dirty)` 弹 `ConfirmDialog("放弃未保存的修改？")`（规格 6.12）。

- [ ] **Step 5: 跑测试 + Commit**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*RecordValidatorTest" --tests "*RecordEditorViewModelTest"` → Expected: 11 个测试通过
```powershell
git add android/app/src; git commit -m "feat(m2): record editor with validation, live calorie preview, last-used type"
```

---

### Task 17: 首次引导（P0）与身体参数（P6）

**Files:**
- Create: `ui/onboarding/OnboardingScreen.kt`、`ui/body/BodyProfileViewModel.kt`、`ui/body/BodyProfileScreen.kt`、`domain/validate/BodyProfileValidator.kt`
- Modify: `MainActivity.kt`（无 profile 时先走 `onboarding`）、`ui/nav/FitRecordNavHost.kt`
- Test: `domain/validate/BodyProfileValidatorTest.kt`、`ui/body/BodyProfileViewModelTest.kt`

**Interfaces:**
- Consumes: `ProfileDao`、`FitRecordTextTheme`
- Produces:
  - `data class BodyProfileInput(gender: String?, age: Int?, heightCm: Double?, weightKg: Double?)`
  - `BodyProfileValidator.validate(input): List<String>`（体重必填且 20–300；身高可空但填则 100–250；年龄可空但填则 10–100）
  - `BodyProfileViewModel.save(input): Boolean`、`observe(): Flow<BodyUi>`
  - `Routes.ONBOARDING` 完成页调 `save` 后 `nav.navigate(Routes.CALENDAR) { popUpTo(ONBOARDING) { inclusive = true } }`

- [ ] **Step 1: 写 `BodyProfileValidatorTest.kt`**

```kotlin
package com.fitrecord.app.domain.validate

import org.junit.Assert.*
import org.junit.Test

class BodyProfileValidatorTest {
    private fun v(weight: Double?, height: Double? = null, age: Int? = null) =
        BodyProfileValidator.validate(BodyProfileInput("M", age, height, weight))

    @Test fun `weight is mandatory`() { assertTrue(v(null).any { it.contains("体重") }) }
    @Test fun `weight bounds 20 to 300`() {
        assertTrue(v(19.9).isNotEmpty()); assertTrue(v(300.1).isNotEmpty()); assertTrue(v(70.0).isEmpty())
    }
    @Test fun `height optional but bounded`() {
        assertTrue(v(70.0, height = null).isEmpty())
        assertTrue(v(70.0, height = 90.0).isNotEmpty())
        assertTrue(v(70.0, height = 251.0).isNotEmpty())
    }
    @Test fun `age optional but bounded`() {
        assertTrue(v(70.0, age = 9).isNotEmpty()); assertTrue(v(70.0, age = 101).isNotEmpty())
        assertTrue(v(70.0, age = 28).isEmpty())
    }
    @Test fun `one decimal weight is accepted and normalized`() {
        assertEquals(70.5, BodyProfileValidator.normalizeWeight("70.5"), 1e-9)
        assertTrue(v(70.5).isEmpty())
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*BodyProfileValidatorTest"` → Expected: 编译失败

- [ ] **Step 2: 实现 `BodyProfileValidator.kt`**

```kotlin
package com.fitrecord.app.domain.validate

data class BodyProfileInput(val gender: String?, val age: Int?, val heightCm: Double?, val weightKg: Double?)

object BodyProfileValidator {
    fun validate(i: BodyProfileInput): List<String> = buildList {
        val w = i.weightKg
        if (w == null) add("体重为必填，否则无法计算卡路里")
        else if (w !in 20.0..300.0) add("体重需在 20–300 kg")
        i.heightCm?.let { if (it !in 100.0..250.0) add("身高需在 100–250 cm") }
        i.age?.let { if (it !in 10..100) add("年龄需在 10–100 岁") }
    }

    fun normalizeWeight(text: String): Double = text.trim().toDoubleOrNull() ?: 0.0
    fun normalizeOptional(text: String): Double? = text.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
}
```

- [ ] **Step 3: 实现 `BodyProfileViewModel` + 页面 + 引导步骤机**

`BodyProfileViewModelTest.kt` 三个断言：
```kotlin
@Test fun `save returns false and writes nothing when invalid`() = runBlocking {
    assertFalse(vm.save(BodyProfileInput("M", null, null, 500.0)))
    assertNull(db.profileDao().profile())
}
@Test fun `save persists weight and updates updatedAt`() = runBlocking {
    assertTrue(vm.save(BodyProfileInput("M", 28, 175.0, 70.0)))
    val p = db.profileDao().profile()!!
    assertEquals(70.0, p.weightKg, 1e-9); assertEquals(0, p.weeklyGoal)
    assertTrue(p.updatedAt > 0)
}
@Test fun `editing existing profile keeps weeklyGoal`() = runBlocking {
    db.profileDao().upsert(UserProfileEntity(weightKg = 70.0, weeklyGoal = 3, updatedAt = 1))
    vm.save(BodyProfileInput("M", 29, 176.0, 71.0))
    assertEquals(3, db.profileDao().profile()!!.weeklyGoal)   // 规格 9：V1 只存不改
}
```

`OnboardingScreen`：`enum class OnboardStep { WELCOME, GENDER, AGE, HEIGHT, WEIGHT, DONE }`，`vm.step: StateFlow<OnboardStep>` + `next()`/`back()`；步骤指示器为 `Row` 内 `size steps.size` 个 6dp 圆点（当前 `primary`，其余 `outline`）；“稍后设置”仅 `WELCOME` 步显示，点击后 `onSkip()` 直接进日历（Banner 兜底引导）。

`BodyProfileScreen`：`Column` + 4 个输入（性别 `SingleChoiceSegmentedButtonRow`，其余 `OutlinedTextField(keyboardType = Decimal)`）+ `Button("保存")`，错误文字取 `validate()` 返回项逐行渲染 `error` 色。

- [ ] **Step 4: 跑测试 + Commit**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*BodyProfile*"` → Expected: 8 个测试通过
```powershell
git add android/app/src; git commit -m "feat(m2): onboarding flow and body profile with bounds validation"
```

---

### Task 18: 运动类型管理与 MET 参考表（P7 / P8 / P8b）

**Files:**
- Create: `ui/types/{TypeListViewModel,TypeListScreen,TypeEditViewModel,TypeEditScreen,MetReferenceSheet}.kt`、`domain/validate/TypeValidator.kt`
- Modify: `ui/nav/FitRecordNavHost.kt`（加 `types`、`type/edit/{id}`）
- Test: `domain/validate/TypeValidatorTest.kt`、`ui/types/TypeListViewModelTest.kt`

**Interfaces:**
- Consumes: `TypeDao`（T5）、`MetReference`（T6）、`ExercisePalette`（T3）
- Produces:
  - `TypeValidator.validate(name: String, met: Double, existing: List<String>): List<String>`（名称 ≤10 字且不重名；MET 0.5–20）
  - `TypeListViewModel.observe(): Flow<List<TypeRow>>`，`data class TypeRow(entity: ExerciseTypeEntity, usageCount: Int, canDelete: Boolean)`
  - `fun archive(id: Long)`、`fun restore(id: Long)`、`fun resetMetToDefault(id: Long)`
  - `TypeEditViewModel.save(): Boolean`（新增/编辑，含 `metOverridden` 标记）

- [ ] **Step 1: 写 `TypeValidatorTest.kt`**

```kotlin
package com.fitrecord.app.domain.validate

import org.junit.Assert.*
import org.junit.Test

class TypeValidatorTest {
    private val existing = listOf("划船机", "瑜伽")

    @Test fun `blank name rejected`() { assertTrue(TypeValidator.validate("", 5.0, existing).isNotEmpty()) }
    @Test fun `name over 10 chars rejected`() {
        assertTrue(TypeValidator.validate("十一个字的名字啊", 5.0, existing).any { it.contains("10") })
    }
    @Test fun `duplicate name rejected including case and space variants`() {
        assertTrue(TypeValidator.validate("划船机", 5.0, existing).any { it.contains("已存在") })
        assertTrue(TypeValidator.validate(" 划船机 ", 5.0, existing).any { it.contains("已存在") })
    }
    @Test fun `renaming to its own current name is allowed`() {
        assertTrue(TypeValidator.validate("划船机", 7.5, existing, currentName = "划船机").isEmpty())
    }
    @Test fun `met bounds 0_5 to 20`() {
        assertTrue(TypeValidator.validate("攀岩", 0.4, existing).any { it.contains("MET") })
        assertTrue(TypeValidator.validate("攀岩", 20.1, existing).any { it.contains("MET") })
        assertTrue(TypeValidator.validate("攀岩", 8.0, existing).isEmpty())
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*TypeValidatorTest"` → Expected: 编译失败

- [ ] **Step 2: 实现 `TypeValidator.kt`**

```kotlin
package com.fitrecord.app.domain.validate

object TypeValidator {
    const val MAX_NAME = 10
    const val MIN_MET = 0.5
    const val MAX_MET = 20.0

    fun validate(name: String, met: Double, existing: List<String>, currentName: String? = null): List<String> = buildList {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) add("名称不能为空")
        else if (trimmed.length > MAX_NAME) add("名称最多 $MAX_NAME 个字")
        else if (trimmed != currentName?.trim() && existing.any { it.trim() == trimmed }) add("该名称已存在")
        if (met !in MIN_MET..MAX_MET) add("MET 需在 $MIN_MET–$MAX_MET")
    }
}
```

- [ ] **Step 3: 写 `TypeListViewModelTest.kt`（重点：被引用的类型不可删除）**

```kotlin
@Test fun `builtin type with usage cannot be deleted but can be archived`() = runBlocking {
    repo.add(LocalDate.of(2026, 10, 12), 1, 30, emptyList(), "")
    val row = vm.rows.first().first { it.entity.id == 1L }
    assertEquals(1, row.usageCount)
    assertFalse(row.canDelete)
}
@Test fun `unused custom type is deletable`() = runBlocking {
    val id = db.typeDao().insertType("攀岩", 8.0, 0xFF123456L, isBuiltin = false, sortOrder = 99)
    val row = vm.rows.first().first { it.entity.id == id }
    assertTrue(row.canDelete)
}
@Test fun `archiving hides type from active list but keeps history`() = runBlocking {
    repo.add(LocalDate.of(2026, 10, 12), 1, 30, emptyList(), "")
    vm.archive(1L)
    assertTrue(db.typeDao().observeActive().first().none { it.id == 1L })
    assertEquals(1, db.trainingDao().rangeOnce(0, Int.MAX_VALUE).size)   // 记录未被删
}
@Test fun `editing met on builtin sets metOverridden flag`() = runBlocking {
    vm.saveMet(1L, 6.0)
    val t = db.typeDao().byId(1L)!!
    assertEquals(6.0, t.met, 1e-9); assertTrue(t.metOverridden)
    vm.resetMetToDefault(1L)
    assertEquals(7.0, db.typeDao().byId(1L)!!.met, 1e-9)
    assertFalse(db.typeDao().byId(1L)!!.metOverridden)
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*TypeListViewModelTest"` → Expected: 编译失败

- [ ] **Step 4: 实现 VM 与三个页面**

- `TypeListViewModel`：`rows = combine(typeDao.observeAllForAdmin(), usageFlow) { … }`；`canDelete = !isBuiltin && usageCount == 0`；`archive()` 前先查 `usageCount`，>0 时只弹“归档”选项（规格 P7）
- `TypeListScreen`：`LazyColumn`，行内 6dp 颜色圆点 + 名称 + `"MET ${met}"` + 内置/自定义/已归档 标签（`caption`）；长按 `DropdownMenu(编辑/归档/删除)`；顶部 `IconButton(Icons.Default.Add)` → `Routes.typeEdit(-1)`
- `TypeEditScreen`：名称、`MetStepper`（`−0.1 / +0.1`，范围 0.5–20）、颜色 8 色单选、默认部位 `TagSelector`、常驻 `TextButton("📖 查参考表")` → `ModalBottomSheet { MetReferenceSheet(onPick = { met = it.met; name = it.name }) }`
- `MetReferenceSheet`：`MetReference.groupByIntensity()` 分组 `LazyColumn`，每行 `名称 | MET | 强度`，点击回填并关闭

- [ ] **Step 5: 跑测试 + 全量回归 + Commit**

Run: `.\gradlew.bat :app:testDebugUnitTest` → Expected: M1+M2 全部测试绿
```powershell
git add android/app/src; git commit -m "feat(m2): exercise type management with MET editor and reference sheet"
```

---

## M3 统计与图表

### Task 19: 图表基础设施与柱状/迷你趋势图

**Files:**
- Create: `ui/charts/ChartModel.kt`、`ui/charts/BarChart.kt`、`ui/charts/Sparkline.kt`、`ui/charts/ChartAxes.kt`
- Test: `ui/charts/ChartModelTest.kt`

**Interfaces:**
- Produces:
  - `data class ChartPoint(label: String, value: Float)`
  - `ChartModel.scaleMax(values: List<Float>, steps: List<Float> = listOf(1f, 2f, 2.5f, 5f, 10f)): Float`（轴最大值向上取整到“好看”刻度）
  - `ChartModel.normalize(values: List<Float>): List<Float>`（0..1，全 0 时返回全 0）
  - `ChartModel.yTicks(max: Float, count: Int = 4): List<Float>`
  - `@Composable fun BarChart(points: List<ChartPoint>, height: Dp, valueLabel: String)`
  - `@Composable fun Sparkline(points: List<ChartPoint>, color: Color, height: Dp)`

- [ ] **Step 1: 写失败测试（纯 JVM，图表数学与渲染解耦）**

```kotlin
package com.fitrecord.app.ui.charts

import org.junit.Assert.*
import org.junit.Test

class ChartModelTest {
    @Test fun `scale max rounds up to nice tick`() {
        assertEquals(50f, ChartModel.scaleMax(listOf(38f)), 0f)
        assertEquals(100f, ChartModel.scaleMax(listOf(76f)), 0f)
        assertEquals(250f, ChartModel.scaleMax(listOf(240f)), 0f)
        assertEquals(10f, ChartModel.scaleMax(listOf(3f, 7f)), 0f)
    }
    @Test fun `scale max of all zero is 1 to avoid div by zero`() {
        assertEquals(1f, ChartModel.scaleMax(listOf(0f, 0f)), 0f)
    }
    @Test fun `normalize maps to unit range`() {
        assertEquals(listOf(0f, 0.5f, 1f), ChartModel.normalize(listOf(0f, 5f, 10f)))
        assertEquals(listOf(0f, 0f), ChartModel.normalize(listOf(0f, 0f)))
    }
    @Test fun `y ticks are ascending and include max`() {
        val t = ChartModel.yTicks(100f, 4)
        assertEquals(listOf(0f, 25f, 50f, 75f, 100f), t)
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ChartModelTest"` → Expected: 编译失败

- [ ] **Step 2: 实现 `ChartModel.kt`（`kotlin.math` 纯函数）**

```kotlin
package com.fitrecord.app.ui.charts

import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow

data class ChartPoint(val label: String, val value: Float)

object ChartModel {
    private val MANTISSAS = listOf(1f, 2f, 2.5f, 5f, 10f)

    fun scaleMax(values: List<Float>, steps: List<Float> = MANTISSAS): Float {
        val raw = values.maxOrNull()?.takeIf { it > 0f } ?: return 1f
        val exp = (log10(raw).toInt())
        val base = 10f.pow(exp.toFloat())
        return steps.map { it * base }.firstOrNull { it >= raw } ?: (10f.pow(exp + 1))
    }

    fun normalize(values: List<Float>): List<Float> {
        val max = values.maxOrNull() ?: 0f
        if (max <= 0f) return values.map { 0f }
        return values.map { it / max }
    }

    fun yTicks(max: Float, count: Int = 4): List<Float> = (0..count).map { ceil(max * it / count) }
}
```

- [ ] **Step 3: 实现 `BarChart.kt` / `Sparkline.kt` / `ChartAxes.kt`（Compose Canvas）**

- `BarChart`：`Canvas(Modifier.fillMaxWidth().height(height))`；`slot = size.width / points.size`；柱宽 `slot * 0.55f`，圆角 `CornerRadius(4f)`；颜色 `primary`，值为 0 时画 `surfaceVariant` 的 2dp 底线；X 轴标签用 `drawText`（`androidx.compose.ui.graphics.drawscope.draw` + `TextMeasurer`）取 `labelSmall` + `onSurfaceVariant`
- `ChartAxes`：`@Composable fun rememberTextMeasurer()` 封装 + `fun YAxis(max: Float, ticks: List<Float>)` 画 4 条水平网格线（`outline`，`PathEffect.dashPathEffect`）
- `Sparkline`：`drawIntoCanvas` 画折线（`Stroke(2f.dp.toPx())`）+ 面积填充（`primary.copy(alpha = 0.12f)`），高度 48dp，无坐标轴
- 所有图表组件签名带 `modifier: Modifier = Modifier`，并在 `semantics { contentDescription = ... }` 里用 `valueLabel` 读出总值（规格 5.3）

- [ ] **Step 4: 写渲染冒烟测试 + Commit**

```kotlin
@Test fun `bar chart renders without crash and exposes total in semantics`() {
    rule.setContent { FitRecordTheme { BarChart(points = listOf(ChartPoint("一", 30f), ChartPoint("二", 60f)), height = 140.dp, valueLabel = "本周 90 分钟") } }
    rule.onNodeWithText("本周 90 分钟").assertExists()
}
```
Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ChartModelTest" --tests "*BarChartTest"` → Expected: 全绿
```powershell
git add android/app/src; git commit -m "feat(m3): chart math, bar chart and sparkline on Compose Canvas"
```

---

### Task 20: 折线图、环形图、雷达图、热力图

**Files:**
- Create: `ui/charts/LineChart.kt`、`ui/charts/DonutChart.kt`、`ui/charts/RadarChart.kt`、`ui/charts/Heatmap.kt`
- Test: `ui/charts/DonutChartMathTest.kt`、`ui/charts/RadarChartMathTest.kt`、`ui/charts/HeatmapTest.kt`

**Interfaces:**
- Produces:
  - `@Composable fun LineChart(points: List<ChartPoint>, height: Dp, onPointClick: (Int) -> Unit)`
  - `DonutMath.slices(values: List<Float>): List<Pair<Float, Float>>`（起止角度，累计到 360）
  - `@Composable fun DonutChart(slices: List<Slice>, centerLabel: String, centerValue: String, onSelect: (Int) -> Unit, size: Dp)`，`data class Slice(label: String, value: Float, color: Long)`
  - `RadarMath.vertex(center: Offset, radius: Float, axisIndex: Int, axisCount: Int, ratio: Float): Offset`
  - `@Composable fun RadarChart(axes: List<String>, values: List<Float>, size: Dp)`（4 环网格）
  - `HeatmapMath.level(segments: Int): Int`（0/1/2/3/≥4 → 0..4）
  - `@Composable fun CalendarHeatmap(cells: List<DayCell>, colors: List<Color>, modifier: Modifier)`

- [ ] **Step 1: 写三个数学/状态测试**

```kotlin
package com.fitrecord.app.ui.charts

import org.junit.Assert.*
import org.junit.Test

class DonutChartMathTest {
    @Test fun `slices cover full circle`() {
        val s = DonutMath.slices(listOf(50f, 30f, 20f))
        assertEquals(3, s.size)
        assertEquals(0f, s.first().first, 0.01f)
        assertEquals(360f, s.last().second, 0.01f)
    }
    @Test fun `empty or zero input yields no slices`() {
        assertTrue(DonutMath.slices(emptyList()).isEmpty())
        assertTrue(DonutMath.slices(listOf(0f, 0f)).isEmpty())
    }
    @Test fun `single value is a full ring`() {
        val s = DonutMath.slices(listOf(7f))
        assertEquals(360f, s.single().second, 0.01f)
    }
}

class RadarChartMathTest {
    @Test fun `eight axes start at top and go clockwise`() {
        val c = androidx.compose.ui.geometry.Offset(100f, 100f)
        val top = RadarMath.vertex(c, 50f, 0, 8, 1f)
        assertEquals(100f, top.x, 0.5f); assertEquals(50f, top.y, 0.5f)
        val right = RadarMath.vertex(c, 50f, 2, 8, 1f)   // 第 3 轴 ≈ 右侧
        assertTrue(right.x > 100f)
    }
    @Test fun `ratio scales radius`() {
        val c = androidx.compose.ui.geometry.Offset(0f, 0f)
        assertEquals(25f, RadarMath.vertex(c, 50f, 0, 8, 0.5f).y.unaryMinus() - 0f, 0.5f)
    }
}

class HeatmapTest {
    @Test fun `segment count maps to five levels`() {
        assertEquals(0, HeatmapMath.level(0)); assertEquals(1, HeatmapMath.level(1))
        assertEquals(2, HeatmapMath.level(2)); assertEquals(3, HeatmapMath.level(3))
        assertEquals(4, HeatmapMath.level(4)); assertEquals(4, HeatmapMath.level(9))
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*DonutChartMathTest" --tests "*RadarChartMathTest" --tests "*HeatmapTest"` → Expected: 编译失败

- [ ] **Step 2: 实现四个图表**

- `DonutMath.slices`：`total <= 0 → emptyList()`；否则 `values.foldIndexed` 累加角度，`angle = v / total * 360f`
- `DonutChart`：`drawArc(oval = center ± (size/2 - stroke/2), startAngle = -90f + from, sweepAngle = to - from, useCenter = false, style = Stroke(width = size * 0.225f))`（内径 55%）；中心两行 `Text`（总时长）；`pointerInput { detectTapGestures { angle = atan2(...) → 选中扇区 } }`
- `LineChart`：`Path().apply { moveTo/lineTo }` + 面积 `path.lineTo(right, bottom); close()` 填 `primary.copy(alpha = 0.12f)`；数据点 `drawCircle(radius = 4.dp.toPx())`，值为 0 时 `style = Stroke(2f)` 空心（规格 2.5）；`onPointClick` 用 `detectTapGestures` 取最近索引
- `RadarMath.vertex`：`angle = -PI/2 + 2PI * axisIndex / axisCount`；`Offset(cx + cos(angle) * radius * ratio, cy + sin(angle) * radius * ratio)`
- `RadarChart`：4 环多边形网格（`drawPath` `outline`）+ 8 条轴线 + 数据多边形（`primary.copy(alpha = 0.20f)` 填充 + `primary` 描边）+ 轴名 `drawText`；`values` 先过 `ChartModel.normalize`
- `CalendarHeatmap`：`Column` 内 6 行 `Row`，每格 `Box(weight(1f).aspectRatio(1f))`，背景色 = `HEAT_LEVELS[level]`（`#E8F5EC / #BFE6CC / #7FCB98 / #45B170 / #1F8A4C` 写入 `Color.kt` 的 `HeatLevels`），格内 `labelSmall` 数字（非 0 才画，规格 5.3）

- [ ] **Step 3: 跑测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*Chart*" --tests "*HeatmapTest"` → Expected: 全绿

- [ ] **Step 4: Commit**

```powershell
git add android/app/src; git commit -m "feat(m3): line, donut, radar charts and calendar heatmap on Canvas"
```

---

### Task 21: 统计页装配（P2）

**Files:**
- Create: `ui/stats/StatsViewModel.kt`；Modify: `ui/stats/StatsScreen.kt`（替换 T13 的占位）、`ui/nav/FitRecordNavHost.kt`
- Test: `ui/stats/StatsViewModelTest.kt`

**Interfaces:**
- Consumes: `StatsRepository.observeStats`（T11）、`SettingsStore.firstDayOfWeek`（T10）、T19/T20 图表组件
- Produces:
  - `StatsViewModel(periodType, anchor)`；`fun onPeriodType(t: PeriodType)`、`fun onShift(delta: Int)`、`fun onCellClick(date: LocalDate)`
  - `data class StatsUi(loading: Boolean, model: StatsUiModel?, canShiftForward: Boolean)`

- [ ] **Step 1: 写失败测试**

```kotlin
@Test fun `default period is the week containing today`() = runBlocking {
    assertEquals(PeriodType.WEEK, vm.uiState.first().model!!.period.type)
    assertEquals(LocalDate.of(2026, 10, 12).toEpochDay().toInt(), vm.uiState.first().model!!.period.range.first)
}
@Test fun `switching to month changes range to whole october`() = runBlocking {
    vm.onPeriodType(PeriodType.MONTH)
    val p = vm.uiState.first().model!!.period
    assertEquals(LocalDate.of(2026, 10, 1).toEpochDay().toInt(), p.range.first)
    assertEquals(LocalDate.of(2026, 10, 31).toEpochDay().toInt(), p.range.last)
    assertEquals("2026年10月", p.label)
}
@Test fun `cannot shift forward into the future`() = runBlocking {
    vm.onPeriodType(PeriodType.MONTH); vm.onShift(-1)
    assertFalse(vm.uiState.first().canShiftForward)   // 已在当前月，不可再向前（+1）
    assertEquals("2026年9月", vm.uiState.first().model!!.period.label)
    vm.onShift(+1)
    assertEquals("2026年10月", vm.uiState.first().model!!.period.label)
}
@Test fun `empty period exposes null model values and empty food text`() = runBlocking {
    vm.onShift(-24)
    val m = vm.uiState.first().model!!
    assertEquals(0, m.summary.segments); assertEquals("", m.foodText)
    assertEquals(7, m.heat.size)                       // 周维度 7 个桶
    assertEquals(8, m.trend.size)                      // 近 8 周
    assertEquals(8, m.byPart.size)
}
@Test fun `trend has 6 buckets in month mode and 8 in week mode`() = runBlocking {
    assertEquals(8, vm.uiState.first().model!!.trend.size)
    vm.onPeriodType(PeriodType.MONTH)
    assertEquals(6, vm.uiState.first().model!!.trend.size)
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*StatsViewModelTest"` → Expected: 编译失败

- [ ] **Step 2: 实现 VM 与页面**

`StatsViewModel`：`combine(periodType, anchor, settings.firstDayOfWeek) { t, a, f -> statsRepo.periodOf(t, a, f) }` → `flatMapLatest { statsRepo.observeStats(it) }` → `map { StatsUi(false, it, canShiftForward = it.period.range.last < LocalDate.now().toEpochDay().toInt()) }`；`onShift` 限制 `delta > 0` 时不得越过当前周期。

`StatsScreen`（`LazyColumn`，卡片顺序按规格 P2）：3 张 `MetricCard`（段数+`Sparkline`、总时长、消耗+`foodText`，`accent = true`）→ `ChartCard("运动频率分布")`（月＝`CalendarHeatmap`，周＝`BarChart`）→ `ChartCard("时长趋势")`（`LineChart`）→ `ChartCard("运动方式占比")`（`DonutChart` + 图例 `Row`）→ `ChartCard("部位训练占比")`（`RadarChart`）。空数据时 `ChartCard` 内容换为 `EmptyState("这个周期还没有训练记录")`（规格 P2 空态）。热力格点击 → `nav.navigate(Routes.day(date))`。

- [ ] **Step 3: 跑测试 + 全量回归 + Commit**

Run: `.\gradlew.bat :app:testDebugUnitTest` → Expected: 全绿
```powershell
git add android/app/src; git commit -m "feat(m3): stats screen assembling five charts with week month period switching"
```

---

## M4 备份恢复与设置

### Task 22: 备份编解码与合并策略（JSON / CSV）

**Files:**
- Create: `domain/backup/{BackupModels,BackupCodec,CsvCodec,MergeResolver}.kt`
- Test: `domain/backup/BackupCodecTest.kt`、`CsvCodecTest.kt`、`MergeResolverTest.kt`（纯 JVM）

**Interfaces:**
- Produces:
  - `@Serializable data class BackupFile(schemaVersion: Int, exportedAt: String, profile: ProfileDto?, exerciseTypes: List<ExerciseTypeDto>, records: List<RecordDto>, dailyNotes: List<DailyNoteDto>)`
  - `BackupCodec.toJson(BackupFile): String` / `BackupCodec.fromJson(String): Result<BackupFile>`（失败返回带原因的 Result）
  - `CsvCodec.write(records: List<RecordDto>): String` / `CsvCodec.parse(text: String): Result<List<RecordDto>>`
  - `enum class ConflictPolicy { SKIP, OVERWRITE }`
  - `MergeResolver.resolve(incoming: List<RecordDto>, existing: List<RecordDto>, policy: ConflictPolicy): MergePlan`
  - `data class MergePlan(toInsert: List<RecordDto>, toUpdate: List<RecordDto>, skipped: Int, errors: List<String>)`
  - `MergeResolver.keyOf(RecordDto): String`（幂等键 = `date|exercise|durationMin|note`）

- [ ] **Step 1: 写 `BackupCodecTest.kt`**

```kotlin
package com.fitrecord.app.domain.backup

import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private fun sample() = BackupFile(
        schemaVersion = 1, exportedAt = "2026-10-12T08:30:00",
        profile = ProfileDto("M", 28, 175.0, 70.0),
        exerciseTypes = listOf(ExerciseTypeDto("划船机", 7.0, false)),
        records = listOf(RecordDto("2026-10-12", "划船机", 7.0, 45, 368.0, listOf("背", "核心"), "x")),
        dailyNotes = listOf(DailyNoteDto("2026-10-12", "今天背有点紧")),
    )

    @Test fun `json round trip preserves all fields`() {
        val back = BackupCodec.fromJson(BackupCodec.toJson(sample())).getOrThrow()
        assertEquals(sample(), back)
    }
    @Test fun `null calories survives round trip`() {
        val src = sample().copy(records = listOf(sample().records.first().copy(calories = null)))
        assertNull(BackupCodec.fromJson(BackupCodec.toJson(src)).getOrThrow().records.first().calories)
    }
    @Test fun `garbage input fails with readable error`() {
        val r = BackupCodec.fromJson("not json at all")
        assertTrue(r.isFailure); assertTrue(r.exceptionOrNull()!!.message!!.contains("JSON"))
    }
    @Test fun `newer schema version is rejected per spec 6_6`() {
        val future = BackupCodec.toJson(sample().copy(schemaVersion = 99))
        val r = BackupCodec.fromJson(future)
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("升级"))
    }
    @Test fun `missing optional dailyNotes defaults to empty`() {
        val json = """{"schemaVersion":1,"exportedAt":"2026-10-12T08:30:00","exerciseTypes":[],"records":[]}"""
        val f = BackupCodec.fromJson(json).getOrThrow()
        assertTrue(f.dailyNotes.isEmpty()); assertNull(f.profile)
    }
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*BackupCodecTest"` → Expected: 编译失败

- [ ] **Step 2: 实现 `BackupModels.kt` 与 `BackupCodec.kt`**

```kotlin
package com.fitrecord.app.domain.backup

import kotlinx.serialization.Serializable

@Serializable data class ProfileDto(val gender: String?, val age: Int?, val heightCm: Double?, val weightKg: Double)
@Serializable data class ExerciseTypeDto(val name: String, val met: Double, val archived: Boolean = false)
@Serializable data class RecordDto(
    val date: String, val exercise: String, val met: Double, val durationMin: Int,
    val calories: Double?, val bodyParts: List<String> = emptyList(), val note: String = "",
)
@Serializable data class DailyNoteDto(val date: String, val text: String)
@Serializable data class BackupFile(
    val schemaVersion: Int, val exportedAt: String, val profile: ProfileDto? = null,
    val exerciseTypes: List<ExerciseTypeDto> = emptyList(),
    val records: List<RecordDto> = emptyList(), val dailyNotes: List<DailyNoteDto> = emptyList(),
)
```

```kotlin
package com.fitrecord.app.domain.backup

import kotlinx.serialization.json.Json

object BackupCodec {
    const val CURRENT_VERSION = 1
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; prettyPrint = true }

    fun toJson(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun fromJson(text: String): Result<BackupFile> = runCatching {
        val parsed = try { json.decodeFromString(BackupFile.serializer(), text) }
                    catch (t: Throwable) { throw IllegalArgumentException("JSON 解析失败：不是有效的备份文件", t) }
        require(parsed.schemaVersion <= CURRENT_VERSION) {
            "备份文件版本为 ${parsed.schemaVersion}，高于本应用支持的 $CURRENT_VERSION，请升级 App 后再导入"
        }
        require(parsed.records.isNotEmpty() || parsed.dailyNotes.isNotEmpty()) {
            "备份文件中没有可导入的训练记录"
        }
        parsed
    }
}
```

- [ ] **Step 3: 写 `CsvCodecTest.kt` 并实现（重点：RFC4180 转义 + UTF-8 BOM）**

```kotlin
@Test fun `header matches spec 4_7 columns`() {
    assertEquals("date,exercise,met,durationMin,calories,bodyParts,note", CsvCodec.HEADER)
}
@Test fun `note with comma and quote is escaped`() {
    val line = CsvCodec.writeLine(RecordDto("2026-10-12", "跑步机", 8.3, 30, 291.0, listOf("全身有氧"), "快,跑\"3\""))
    assertEquals("""2026-10-12,跑步机,8.3,30,291.0,全身有氧,"快,跑""3"""""", line)
}
@Test fun `round trip keeps all rows`() {
    val rows = listOf(RecordDto("2026-10-12", "瑜伽", 2.5, 60, null, listOf("核心"), "a,b"),
                     RecordDto("2026-10-13", "游泳", 5.8, 30, 203.0, emptyList(), ""))
    val parsed = CsvCodec.parse(CsvCodec.BOM + CsvCodec.HEADER + "\n" + rows.joinToString("\n") { CsvCodec.writeLine(it) }).getOrThrow()
    assertEquals(rows, parsed)
}
@Test fun `null calories parses back as null`() {
    val parsed = CsvCodec.parse(CsvCodec.HEADER + "\n" + CsvCodec.writeLine(RecordDto("2026-10-12", "瑜伽", 2.5, 60, null, emptyList(), ""))).getOrThrow()
    assertNull(parsed.single().calories)
}
@Test fun `wrong header is rejected`() {
    assertTrue(CsvCodec.parse("a,b,c\n1,2,3").isFailure)
}
@Test fun `short row reports line number per spec 6_5`() {
    val r = CsvCodec.parse(CsvCodec.HEADER + "\n2026-10-12,跑步机\n")
    assertTrue(r.isFailure); assertTrue(r.exceptionOrNull()!!.message!!.contains("第 2 行"))
}
```
实现要点：`writeLine` 对含 `,` `"` `\n` 的字段加双引号并将 `"` 变 `""`；`parse` 用字符扫描而非 `split(",")`（否则带逗号字段会错），`BOM = "\uFEFF"`，错误信息带行号。

- [ ] **Step 4: 写 `MergeResolverTest.kt` 并实现**

```kotlin
@Test fun `identical key is skipped by default`() {
    val existing = listOf(RecordDto("2026-10-12", "跑步机", 8.3, 30, 291.0, emptyList(), ""))
    val plan = MergeResolver.resolve(existing, existing, ConflictPolicy.SKIP)
    assertTrue(plan.toInsert.isEmpty()); assertEquals(1, plan.skipped)
}
@Test fun `overwrite policy updates the existing row`() {
    val a = listOf(RecordDto("2026-10-12", "跑步机", 8.3, 30, 291.0, emptyList(), "旧"))
    val b = listOf(RecordDto("2026-10-12", "跑步机", 8.3, 30, 291.0, emptyList(), "新"))
    val plan = MergeResolver.resolve(b, a, ConflictPolicy.OVERWRITE)
    assertEquals(1, plan.toUpdate.size); assertEquals(0, plan.skipped)
}
@Test fun `new rows are inserted and unknown types are reported not dropped`() {
    val incoming = listOf(RecordDto("2026-10-11", "攀岩", 8.0, 40, 373.0, listOf("背"), ""))
    val plan = MergeResolver.resolve(incoming, emptyList(), ConflictPolicy.SKIP)
    assertEquals(1, plan.toInsert.size)
    assertTrue(plan.errors.any { it.contains("攀岩") })   // 需自动建类型（规格 4.7）
}
@Test fun `invalid rows go to errors and do not block the batch`() {
    val bad = listOf(RecordDto("2026-13-45", "跑步机", 8.3, 30, null, emptyList(), ""))
    val plan = MergeResolver.resolve(bad, emptyList(), ConflictPolicy.SKIP)
    assertTrue(plan.toInsert.isEmpty()); assertEquals(1, plan.errors.size)
}
@Test fun `key ignores calories because it is derived`() {
    val a = listOf(RecordDto("2026-10-12", "跑步机", 8.3, 30, 100.0, emptyList(), ""))
    val b = listOf(RecordDto("2026-10-12", "跑步机", 8.3, 30, 999.0, emptyList(), ""))
    assertEquals(1, MergeResolver.resolve(b, a, ConflictPolicy.SKIP).skipped)
}
```
实现要点：`keyOf` 不包含 `calories`（它由 MET×体重×时长 派生，换手机后体重不同会不同）；`errors` 逐条带行号；`MergePlan.total = toInsert.size + toUpdate.size`。

- [ ] **Step 5: 跑全部备份测试 + 全量回归 + Commit**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*Backup*" --tests "*Csv*" --tests "*Merge*"` → Expected: 16 个测试通过
```powershell
git add android/app/src; git commit -m "feat(m4): json/csv backup codec with rfc4180 escaping and idempotent merge resolver"
```

---

### Task 23: 备份页与 SAF 导入导出（P9）

**Files:**
- Create: `data/repo/BackupRepository.kt`、`ui/backup/BackupViewModel.kt`、`ui/backup/BackupScreen.kt`
- Modify: `ui/nav/FitRecordNavHost.kt`（加 `backup`）
- Test: `data/repo/BackupRepositoryTest.kt`（Robolectric，用内存 `ByteArray` 流）

**Interfaces:**
- Consumes: `BackupCodec`/`CsvCodec`/`MergeResolver`（T22）、全部 DAO
- Produces:
  - `suspend fun exportJson(stream: OutputStream): Int`（返回条数）、`suspend fun exportCsv(stream: OutputStream): Int`
  - `suspend fun preview(stream: InputStream, policy: ConflictPolicy): MergePreview`
  - `suspend fun apply(stream: InputStream, policy: ConflictPolicy): MergeOutcome`（单事务，失败全回滚）
  - `data class MergePreview(total: Int, toInsert: Int, toUpdate: Int, skipped: Int, errors: List<String>)`
  - `sealed interface BackupUi { Idle, Previewing(preview), Working(progress), Done(message), Failed(reason) }`
  - 默认文件名 `fitrecord-YYYYMMDD-HHmm.json|csv`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.fitrecord.app.data.repo

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

// @RunWith(AndroidJUnit4::class) @Config(sdk = [34])，@Before 同 DayRecordViewModelTest（内存库 + 种子 + profile）
@Test fun `export then import into empty database restores identical rows`() = runBlocking {
    training.add(day, 1, 45, listOf(2, 5), "note")
    training.add(day.plusDays(1), 4, 30, listOf(8), "")
    val out = ByteArrayOutputStream()
    assertEquals(2, backup.exportJson(out))

    // 清空后回导
    db.trainingDao().deleteAll()
    val outcome = backup.apply(ByteArrayInputStream(out.toByteArray()), ConflictPolicy.SKIP)
    assertEquals(2, outcome.inserted)
    assertEquals(0, outcome.errors)
    assertEquals(2, db.trainingDao().rangeOnce(0, Int.MAX_VALUE).size)
}

@Test fun `re-import is a no-op thanks to idempotent key`() = runBlocking {
    training.add(day, 1, 45, listOf(2), "")
    val out = ByteArrayOutputStream(); backup.exportJson(out)
    val again = backup.apply(ByteArrayInputStream(out.toByteArray()), ConflictPolicy.SKIP)
    assertEquals(0, again.inserted); assertEquals(1, again.skipped)
}

@Test fun `import creates unknown exercise type automatically`() = runBlocking {
    val json = BackupCodec.toJson(BackupFile(1, "", null, listOf(ExerciseTypeDto("攀岩", 8.0)),
        listOf(RecordDto("2026-10-12", "攀岩", 8.0, 40, 373.0, listOf("背"), ""))))
    val r = backup.apply(ByteArrayInputStream(json.toByteArray()), ConflictPolicy.SKIP)
    assertEquals(1, r.inserted)
    assertNotNull(db.typeDao().byName("攀岩"))
    assertEquals(8.0, db.typeDao().byName("攀岩")!!.met, 1e-9)
}

@Test fun `failed import rolls back the whole transaction`() = runBlocking {
    training.add(day, 1, 45, listOf(2), "")
    val bad = BackupCodec.toJson(BackupFile(1, "", null, emptyList(),
        listOf(RecordDto("2026-10-11", "划船机", 7.0, 30, 245.0, emptyList(), ""),
               RecordDto("2026-13-45", "划船机", 7.0, 30, 245.0, emptyList(), ""))))
    assertThrows(IllegalStateException::class.java) {
        runBlocking { backup.applyStrict(ByteArrayInputStream(bad.toByteArray()), ConflictPolicy.SKIP) }
    }
    assertEquals(1, db.trainingDao().rangeOnce(0, Int.MAX_VALUE).size)   // 未增加
}

@Test fun `csv export uses bom and header`() = runBlocking {
    training.add(day, 1, 45, listOf(2), "")
    val out = ByteArrayOutputStream(); backup.exportCsv(out)
    val text = out.toString("UTF-8")
    assertTrue(text.startsWith(CsvCodec.BOM))
    assertTrue(text.contains(CsvCodec.HEADER))
}

@Test fun `preview reports counts without writing`() = runBlocking {
    val out = ByteArrayOutputStream(); training.add(day, 1, 45, listOf(2), ""); backup.exportJson(out)
    db.trainingDao().deleteAll()
    val p = backup.preview(ByteArrayInputStream(out.toByteArray()), ConflictPolicy.SKIP)
    assertEquals(1, p.toInsert); assertEquals(0, db.trainingDao().rangeOnce(0, Int.MAX_VALUE).size)
}
```
> `applyStrict` 是 `apply` 的"遇错即抛"变体（`@Transaction` 包整个写入），用于验证回滚；UI 默认调 `apply`（返回 outcome，不抛）。

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*BackupRepositoryTest"` → Expected: 编译失败

- [ ] **Step 2: 实现 `BackupRepository.kt`**

要点：
- `@Dao interface BackupDao { @Transaction suspend fun inTransaction(block: suspend () -> Unit) }` 不可行（Room DAO 不能接 lambda）→ 改用 `db.withTransaction { … }`（`androidx.room.withTransaction`，来自 `room-ktx`，已在依赖中）
- 因此 `BackupRepository` 构造参需持有 `db: FitRecordDatabase`（而非只拿 DAO）
- `toDto/toEntity` 双向映射：`date` 用 `LocalDate.parse` / `toString()`；`bodyParts` 按名称查 `body_part.id`，未知名称自动建类型（`isBuiltin = false`，颜色 `ExercisePalette.colorForId(newId)`）
- `calories == null` 时按当前体重重算（导入到新设备体重可能不同）
- 导入日志写 `MetaEntity("last_import", "$now|$inserted|$skipped|$errors")`，P9 最多读回 5 条

- [ ] **Step 3: 实现 SAF 与页面**

```kotlin
// BackupScreen.kt
val exportJsonLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/json")) { uri ->
    uri?.let { vm.exportJson(it) }        // contentResolver.openOutputStream(uri)
}
val importLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.preview(it) } }

Button("JSON 完整备份") { exportJsonLauncher.launch(vm.suggestedName("json")) }
Button("CSV 记录表")   { exportCsvLauncher.launch(vm.suggestedName("csv")) }
Button("选择文件")     { importLauncher.launch(arrayOf("application/json", "text/comma-separated-values",
                                                       "application/octet-stream", "text/csv")) }
Row { RadioButton(SKIP); RadioButton(OVERWRITE) }
if (ui is BackupUi.Previewing) PreviewCard(ui.preview) { confirm -> vm.apply(confirm) }
```
- 用户取消选择：`uri == null` → 直接返回，不弹错误（规格 6.7）
- `Working` 态显示 `LinearProgressIndicator` + `Text("已写入 n/总")`；`Failed` 显示前 5 条错误行（规格 6.5/6.8）
- `suggestedName` 用 `DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")`，不硬编码当前时间到 UI

- [ ] **Step 4: 跑测试 + Commit**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*BackupRepositoryTest"` → Expected: 6 个测试通过
```powershell
git add android/app/src; git commit -m "feat(m4): backup repository over SAF with preview, transactional import and rollback"
```

---

### Task 24: 我的页与外观设置（P3 / P10）

**Files:**
- Create: `ui/profile/ProfileViewModel.kt`、`ui/appearance/AppearanceViewModel.kt`、`ui/appearance/AppearanceScreen.kt`、`ui/fatal/DbUnrecoverableScreen.kt`
- Modify: `ui/profile/ProfileScreen.kt`、`ui/nav/FitRecordNavHost.kt`、`MainActivity.kt`、`FitRecordApp.kt`
- Test: `ui/appearance/AppearanceViewModelTest.kt`、`ui/fatal/FatalStateTest.kt`

**Interfaces:**
- Consumes: `SettingsStore`（T10）、`StatsDao`（累计总账）、`ProfileDao`
- Produces:
  - `ProfileUi(weightKg, heightCm, age, gender, typeCount, customCount, partCount, lifetime: LifetimeStats)`
  - `data class LifetimeStats(days: Int, segments: Int, minutes: Int)`
  - `AppearanceViewModel.setTheme(mode: ThemeMode)`、`setFirstDay(day: DayOfWeek)`、`uiState: StateFlow<AppearanceUi>`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.fitrecord.app.ui.appearance

// @RunWith(AndroidJUnit4::class) @Config(sdk = [34])，内存库 + SettingsStore
@Test fun `defaults are follow-system and monday`() = runBlocking {
    val s = vm.uiState.first()
    assertEquals(ThemeMode.SYSTEM, s.themeMode); assertEquals(DayOfWeek.MONDAY, s.firstDay)
}
@Test fun `set theme persists and is observable`() = runBlocking {
    vm.setTheme(ThemeMode.DARK)
    assertEquals(ThemeMode.DARK, vm.uiState.first().themeMode)
    assertEquals(ThemeMode.DARK, settings.themeMode.first())
}
@Test fun `set first day to sunday shifts week start`() = runBlocking {
    vm.setFirstDay(DayOfWeek.SUNDAY)
    assertEquals(DayOfWeek.SUNDAY, vm.uiState.first().firstDay)
    // 日历页依赖同一 flow，因此切换后周视图首日变周日（规格 D9）
    assertEquals(LocalDate.of(2026, 10, 11),
        WeekUtil.weekStart(LocalDate.of(2026, 10, 12), DayOfWeek.SUNDAY))
}
@Test fun `theme index maps to enum order`() {
    assertEquals(ThemeMode.entries.toTypedArray().toList(), listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK))
}
```

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*AppearanceViewModelTest"` → Expected: 编译失败

- [ ] **Step 2: 实现两个 VM 与两个页面**

- `AppearanceScreen`：`Column` 内两个 `Card`：主题（`SegmentedSwitch(listOf("跟随系统","浅色","深色"), …)`）、周起始日（`SegmentedSwitch(listOf("周一","周日"), …)`）；卡片标题用 `titleLarge`，下方 `caption` 说明
- `ProfileScreen`：身体参数卡（`weightKg == null` 时整卡换成 `Button("完善身体参数")`）+ 5 行入口卡（运动类型/锻炼部位/备份/外观/关于），每行末尾 `Icons.Default.KeyboardArrowRight`；页脚 `labelSmall` 累计总账
- “关于”行弹 `ConfirmDialog` 样式的信息弹窗：三行文案“无网络请求・无广告・数据仅存本机” + `versionName (versionCode)`（从 `BuildConfig` 取，需在 `app/build.gradle.kts` 加 `buildFeatures { buildConfig = true }`）
- `MainActivity`：`settings.snapshot()` 已在 T13 接入，本任务确认 `themeMode` 变化时**不重启 Activity**（`collectAsState` 驱动重组）

- [ ] **Step 3: 只读模式与数据库不可恢复页（规格 6.9 / 6.10）**

这是全计划唯一尚未落地的错误处理项，必须在本任务完成，否则实现与规格 6 不一致：

1. `MainActivity` 订阅 `container.readOnly`；为 `true` 时在 `FitRecordRoot` 的 `Scaffold` 顶部插入红色横幅：

```kotlin
@Composable
private fun ReadOnlyBar() {
    Surface(color = MaterialTheme.colorScheme.error) {
        Row(
            Modifier.fillMaxWidth().padding(Dimens.cardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.cardGap),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
            Text(
                "数据升级失败，请先导出数据",
                color = MaterialTheme.colorScheme.onError,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
```

2. `readOnly == true` 时：`CalendarScreen` 的 FAB `enabled = false`；`RecordEditorViewModel.save()` 首行 `if (readOnly) return false` 并弹 Snackbar"当前为只读模式"。
3. `DbUnrecoverableScreen`：`FitRecordApp.onCreate` 里捕获数据库首次访问异常并置 `fatal: StateFlow<FatalReason?>`；原因为 `SQLITE_NOTADB` 时 `MainActivity` 整屏换成该页，两个按钮：
   - "从备份文件导入" → `nav.navigate(Routes.BACKUP)`（导入走新建库路径，只读模式不阻止）
   - "重建空库" → `ConfirmDialog(destructive = true, confirmLabel = "确认清空")`，确认后 `deleteDatabase(DB_NAME)` + `finishAffinity()` + `startActivity(packageManager.getLaunchIntentForPackage(packageName))`（不引入 ProcessPhoenix 等额外依赖）

`FatalStateTest.kt`：
```kotlin
package com.fitrecord.app.ui.fatal

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitrecord.app.AppContainer
import com.fitrecord.app.data.db.FitRecordDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FatalStateTest {
    @Test fun `read only flag and fatal reason are set together`() {
        val container = AppContainer(ApplicationProvider.getApplicationContext())
        container.markReadOnlyForTest(IllegalStateException("migration failed"))
        assertTrue(container.readOnly.value)
        assertEquals(FatalReason.MigrationFailed, container.fatal.value)
    }
    @Test fun `recreate deletes the database file`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getDatabasePath(FitRecordDatabase.DB_NAME).also { it.parentFile?.mkdirs() }.writeBytes(byteArrayOf(1, 2, 3))
        ctx.deleteDatabase(FitRecordDatabase.DB_NAME)
        assertFalse(ctx.getDatabasePath(FitRecordDatabase.DB_NAME).exists())
    }
}
```
> `markReadOnlyForTest` 在 `AppContainer` 里写为
> `@VisibleForTesting internal fun markReadOnlyForTest(t: Throwable) { _readOnly.value = true; _fatal.value = FatalReason.MigrationFailed }`；
> `enum class FatalReason { MigrationFailed, DatabaseCorrupt }` 定义在 `ui/fatal/DbUnrecoverableScreen.kt`。

- [ ] **Step 4: 跑测试 + 全量回归 + Commit**

Run: `.\gradlew.bat :app:testDebugUnitTest` → Expected: 全绿
```powershell
git add android/app/src android/app/build.gradle.kts
git commit -m "feat(m4): profile hub, appearance settings, read-only banner and unrecoverable-db screen"
```

---

## M5 出包与验收

### Task 25: 启动图标、Manifest 收尾与最终 release

**Files:**
- Create: `res/drawable/ic_launcher_foreground.xml`（细化）、`res/mipmap-anydpi-v26/*.xml`（确认存在）
- Modify: `app/build.gradle.kts`（`versionCode = 1`、`versionName = "1.0"` 确认）、`res/values/strings.xml`（全量文案）
- Test: `ManifestContractTest.kt`（签名与包体内容由 Step 4 的 `apksigner` / `aapt2` 命令验证，不写单测）

**Interfaces:**
- Consumes: 前面全部
- Produces: 可安装的 `app-release.apk`，满足规格 §8 的 8 条验收

- [ ] **Step 1: 写 Manifest 契约测试（把 DoD 第 8 条固定成代码）**

```kotlin
package com.fitrecord.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ManifestContractTest {
    private fun manifest() = ApplicationProvider.getApplicationContext<android.content.Context>()
        .packageManager.getPackageInfo(ApplicationProvider.getApplicationContext<android.content.Context>().packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS)

    @Test fun `no internet permission is declared`() {
        val perms = manifest().requestedPermissions.orEmpty().toList()
        assertFalse("发现网络权限：$perms", perms.contains(android.Manifest.permission.INTERNET))
        assertFalse(perms.contains("android.permission.ACCESS_NETWORK_STATE"))
    }
    @Test fun `no external storage write permission`() {
        val perms = manifest().requestedPermissions.orEmpty().toList()
        assertTrue(perms.none { it.contains("WRITE_EXTERNAL_STORAGE") })
        assertTrue(perms.none { it.contains("READ_MEDIA") })
    }
    @Test fun `min and target sdk match spec`() {
        val info = manifest().applicationInfo!!
        assertEquals(26, android.os.Build.VERSION_CODES.O)   // minSdk 26
        assertTrue(info.targetSdkVersion >= 34)
    }
}
```
> 第三个测试只断言 `targetSdkVersion`：Robolectric 读不到 `minSdkVersion`（它属于 manifest 合并前的 gradle 配置），
> minSdk 的正确性由 Step 4 的 `aapt2 dump badging` 输出 `sdkVersion:'26'` 兼顶。

- [ ] **Step 2: 跑契约测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "*ManifestContractTest"` → Expected: 3 个测试通过（若失败，删除 Manifest 中多余权限）

- [ ] **Step 3: 全量测试 + release 构建**

```powershell
cd "d:\桌面（2）\FitRecord\android"; . .\env.ps1
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease --rerun-tasks
```
Expected: `BUILD SUCCESSFUL`；`app\build\outputs\apk\release\app-release.apk` 存在

- [ ] **Step 4: 验证签名与包体内容（DoD 1/2/8）**

```powershell
$bt = "D:\insbuild\android-sdk\build-tools\35.0.0"
& "$bt\apksigner.bat" verify --print-certs "app\build\outputs\apk\release\app-release.apk"
& "$bt\aapt2.exe" dump badging "app\build\outputs\apk\release\app-release.apk" | Select-String -Pattern "package:|sdkVersion:|targetSdkVersion:|uses-permission|application-label"
```
Expected：
- `Signer #1 certificate DN: CN=FitRecord, …`（不是 Android debug key）
- `package: name='com.fitrecord.app' versionCode='1' versionName='1.0'`
- `sdkVersion:'26'` / `targetSdkVersion:'35'`
- 输出中**没行** `uses-permission` 包含 `INTERNET`
- `application-label:'轻体记'`

- [ ] **Step 5: 核对规格 §8 验收清单并记录差异**

逐条对照 `docs/plan/2026-10-12-fitrecord-ui-design.md` §8，把结果写进 `docs/plan/v1-acceptance.md`：每条一行，标 `PASS` / `FAIL(原因)` / `MANUAL(需真机)`。其八条中 2、3 需真机，标 `MANUAL` 并在安装说明里提醒。

- [ ] **Step 6: 写安装说明并提交**

`docs/plan/安装说明.md`：APK 绝对路径、如何传到手机（USB `adb install -r` 或手动拷贝）、首次安装“未知来源”提示、升级时签名一致性提醒（`keystore.properties` 与 `.keystore` 必须备份，丢了无法覆盖安装）、卸载重装后用 JSON 恢复数据的步骤。

```powershell
cd "d:\桌面（2）\FitRecord"
git add docs android; git commit -m "chore(m5): release v1 apk built and signed; acceptance checklist + install guide"
```

---

## 附：任务依赖与并行建议

```
T1 → T2 → T3 → T4                    （串行，M0 基座）
        T5 → T6 → ┬→ T7 ─┐           （T7 依赖 T5；T8 依赖 T6+T7）
                  └→ T9 ─┴→ T8 → T11 → T12
                             T10 ──────
T11 → T13 → T14 → T15 → T16 → T17 → T18        （M2 串行，UI 递进）
T19 → T20 → T21                                 （M3，可与 T17/T18 并行）
T22 → T23                                       （M4，可与 M3 并行）
T24, T25 收尾                                  （必须最后）
```
可并行组：**{T17, T19}**、**{T18, T20}**、**{T21, T22}**。其余保持串行。

## 附：常见失败与处置

| 现象 | 原因 | 处置 |
|---|---|---|
| `JAVA_HOME is not set` / 编译报 `Unsupported class file major version` | 忘了 dot-source `env.ps1`，或 `org.gradle.java.home` 路径写错 | 重跑 `. .\env.ps1`；核对 `gradle.properties` 里的 `D:/insbuild/jdk17/jdk-17.0.20.1+1` |
| `SDK location not found` | `local.properties` 缺失或被 gitignore 后未重建 | 重写 `sdk.dir=D:/insbuild/android-sdk` |
| KSP 报 `This version (x) of KSP is not compatible with Kotlin` | `ksp` 版本未与 `kotlin = 2.0.21` 对齐 | 严格用 `2.0.21-1.0.28` |
| Compose 报 `Compose Compiler not found` | 忘了 `kotlin-compose` 插件（Kotlin 2.x 必需） | 确认 `plugins {}` 里有 `alias(libs.plugins.kotlin.compose)` |
| Robolectric 报 `No such method: android.os.StrictMode` 或下载 `android-all` 失败 | 未设 `sdk = [34]` 或网络抖动 | 保留 `@Config(sdk = [34])`；重试一次让 `android-all-instrumented-14-*` 下完 |
| Room 报 `Cannot figure out how to associate rows` | `@Relation` / 手写字段名不匹配 | 改用 `RecordWithType` 半对象（T5 已定），不要加 `@Relation` |
| `app/schemas/.../1.json` 未生成 | `exportSchema` 或 `ksp { arg(...) }` 缺失 | 补 `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` |
| 路径含中文导致 `aapt2` 异常 | 工作区在 `d:\桌面（2）\` | 把工程拷到 `D:\fitrecord\` 构建，产物再拷回（技术选型风险 #2） |

---

**计划结束。共 25 个任务，每个任务以“可独立验收的交付物 + 测试通过 + commit”收尾。**
