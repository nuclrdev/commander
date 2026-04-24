@echo off
setlocal EnableDelayedExpansion

set JAVA_HOME=C:\tools\java\jdk-21.0.2+13
set SCRIPT_DIR=%~dp0
for %%i in ("%SCRIPT_DIR%..\..") do set PROJECT_ROOT=%%~fi
set OUT_DIR=%SCRIPT_DIR%dist
set APP_NAME=NuclrCommander
set APP_OUT=%OUT_DIR%\%APP_NAME%

echo === Nuclr Commander - Windows Build ===
echo Project root : %PROJECT_ROOT%
echo Output       : %APP_OUT%
echo.

:: Maven build
echo [1/5] Maven build...
pushd "%PROJECT_ROOT%"
call mvn clean package -q
if errorlevel 1 (echo [ERROR] Maven build failed & popd & exit /b 1)
popd
echo       Done.

:: Locate shaded JAR (exclude original- prefix from maven-shade)
set JAR_FILE=
for /f "delims=" %%f in ('dir /b "%PROJECT_ROOT%\target\*.jar" ^| findstr /v "^original-"') do set JAR_FILE=%%f
if "%JAR_FILE%"=="" (echo [ERROR] Shaded JAR not found in target\ & exit /b 1)
echo [2/5] JAR: %JAR_FILE%

:: Prepare output layout
if exist "%APP_OUT%" rmdir /s /q "%APP_OUT%"
mkdir "%APP_OUT%\app"
copy "%PROJECT_ROOT%\target\%JAR_FILE%" "%APP_OUT%\app\" > nul

:: Build trimmed JRE with jlink (Multi-Release: true in the fat JAR means Spring
:: virtual-thread detection now works correctly with a jlink runtime)
echo [3/5] Building JRE with jlink...
"%JAVA_HOME%\bin\jlink.exe" ^
    --module-path "%JAVA_HOME%\jmods" ^
    --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.crypto.mscapi,jdk.zipfs,jdk.localedata,jdk.accessibility,jdk.net ^
    --no-header-files ^
    --no-man-pages ^
    --strip-debug ^
    --compress=zip-6 ^
    --output "%APP_OUT%\runtime"
if errorlevel 1 (echo [ERROR] jlink failed & exit /b 1)
echo       Done.

:: Copy plugins (PluginLoader scans ./plugins relative to working dir)
echo [4/5] Copying plugins and assets...
xcopy /s /y /q "%PROJECT_ROOT%\plugins\*.zip" "%APP_OUT%\plugins\" > nul
xcopy /s /y /q "%PROJECT_ROOT%\plugins\*.sig" "%APP_OUT%\plugins\" > nul
xcopy /s /y /q "%PROJECT_ROOT%\data" "%APP_OUT%\data\" > nul

:: Compile native launcher via PowerShell + C#
echo [5/5] Compiling launcher...
echo Add-Type -ErrorAction Stop -Path '%SCRIPT_DIR%launcher.cs' -OutputAssembly '%APP_OUT%\%APP_NAME%.exe' -OutputType WindowsApplication > "%TEMP%\build-launcher.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -File "%TEMP%\build-launcher.ps1"
if errorlevel 1 (echo [ERROR] Launcher compilation failed & del "%TEMP%\build-launcher.ps1" & exit /b 1)
del "%TEMP%\build-launcher.ps1"
echo       Done.

echo.
echo === Build complete ===
echo Executable: %APP_OUT%\%APP_NAME%.exe
