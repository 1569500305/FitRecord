# FitRecord 构建环境变量。用法：在 PowerShell 里执行  . .\env.ps1
# 系统 JAVA_HOME 指向已不存在的 C:\Program Files\Java\jdk-14.0.2，必须在此覆盖为 JDK 17。
$env:JAVA_HOME    = "D:\insbuild\jdk17\jdk-17.0.20.1+1"
$env:ANDROID_HOME = "D:\insbuild\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;" + $env:Path
Write-Host "JAVA_HOME    = $env:JAVA_HOME"
Write-Host "ANDROID_HOME = $env:ANDROID_HOME"
java -version
