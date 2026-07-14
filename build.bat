@echo off
setlocal

set "GRADLE_EXE=C:\Users\stepa\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat"

if not exist "%GRADLE_EXE%" (
    echo Gradle 8.9 not found at %GRADLE_EXE%
    echo Adjust GRADLE_EXE in this script or run "gradle wrapper" once to install it.
    exit /b 1
)

echo === Building debug APK ===
call "%GRADLE_EXE%" -p "%~dp0." assembleDebug
if errorlevel 1 goto :error

echo === Building release APK ===
call "%GRADLE_EXE%" -p "%~dp0." assembleRelease
if errorlevel 1 goto :error

echo.
echo Build succeeded.
echo Debug APK:   app\build\outputs\apk\debug\app-debug.apk
echo Release APK: app\build\outputs\apk\release\app-release.apk
exit /b 0

:error
echo.
echo Build FAILED.
exit /b 1
