# FitRecord 构建环境。用法：在 PowerShell 里执行  . .\env.ps1
#
# 为什么需要这个脚本（台账 P14/P18）：
#  1) 系统 JAVA_HOME 指向已不存在的 C:\Program Files\Java\jdk-14.0.2，必须覆盖为 JDK 17。
#  2) 工作区路径含中文（d:\桌面（2））且用户目录含中文（C:\Users\李勋伟）。
#     AGP 直接拒绝前者；后者会让 Gradle 的测试 worker 以非 ASCII classpath 启动，
#     报 ClassNotFoundException: worker...GradleWorkerMain。
#     解决：用目录联接（不复制数据、可逆）提供 ASCII 入口。
#
# 因此：**构建与测试一律从 D:\fitrecord\android 运行**，不要从中文路径运行。

$ErrorActionPreference = "Continue"

$env:JAVA_HOME    = "D:\insbuild\jdk17\jdk-17.0.20.1+1"
$env:ANDROID_HOME = "D:\insbuild\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

# --- ASCII 联接：Gradle 用户主目录 ---
$realGradleHome = Join-Path $env:USERPROFILE ".gradle"
$asciiGradleHome = "D:\gh"
if (-not (Test-Path (Join-Path $asciiGradleHome "wrapper"))) {
    if (Test-Path $asciiGradleHome) {
        Write-Warning "D:\gh 已存在但不是联接，请手动检查后再继续"
    } else {
        New-Item -ItemType Junction -Path $asciiGradleHome -Target $realGradleHome | Out-Null
    }
}
$env:GRADLE_USER_HOME = $asciiGradleHome

# --- ASCII 联接：项目根 ---
$projectRoot = Split-Path -Parent $PSScriptRoot          # d:\桌面（2）\FitRecord
$asciiProject = "D:\fitrecord"
if (-not (Test-Path (Join-Path $asciiProject "android\gradlew.bat"))) {
    if (Test-Path $asciiProject) {
        Write-Warning "D:\fitrecord 已存在但不是联接，请手动检查后再继续"
    } else {
        New-Item -ItemType Junction -Path $asciiProject -Target $projectRoot | Out-Null
    }
}

$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;" + $env:Path

Write-Host "JAVA_HOME        = $env:JAVA_HOME"
Write-Host "ANDROID_HOME     = $env:ANDROID_HOME"
Write-Host "GRADLE_USER_HOME = $env:GRADLE_USER_HOME"
Write-Host "构建请切到 ASCII 路径： cd $asciiProject\android"
java -version
