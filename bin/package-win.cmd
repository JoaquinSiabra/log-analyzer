@echo off
setlocal enabledelayedexpansion

rem Builds a self-contained Windows package using the JDK jpackage tool.
rem Output folder: target\dist\LogArgos

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."

cd /d "%PROJECT_DIR%" || exit /b 1

rem Prefer a full JDK 21 for packaging (jpackage + jlink work best there)
set "PREFERRED_JDK=C:\Program Files\Java\jdk-21.0.1"
if exist "%PREFERRED_JDK%\bin\jpackage.exe" (
  set "JAVA_HOME=%PREFERRED_JDK%"
)

echo Using JAVA_HOME=%JAVA_HOME%

rem 1) Build jars
call mvnw.cmd -DskipTests package
if errorlevel 1 exit /b 1

rem 2) Locate jpackage
set "JPACKAGE_EXE="
if not "%JAVA_HOME%"=="" if exist "%JAVA_HOME%\bin\jpackage.exe" set "JPACKAGE_EXE=%JAVA_HOME%\bin\jpackage.exe"
if "%JPACKAGE_EXE%"=="" for %%I in (jpackage.exe) do set "JPACKAGE_EXE=%%~$PATH:I"

if "%JPACKAGE_EXE%"=="" (
  echo ERROR: No se ha encontrado jpackage.exe.
  echo - Instala un JDK (no JRE) 16 o superior.
  echo - Configura JAVA_HOME o anade el bin al PATH.
  exit /b 2
)

for %%F in ("%JPACKAGE_EXE%") do echo Using jpackage=%%~fF
"%JPACKAGE_EXE%" --version

rem 3) Package GUI app (bundles a JVM runtime automatically)
set "INPUT_DIR=target"
set "DEST_DIR=target\dist"
set "APP_NAME=LogArgos"
set "MAIN_JAR=logargos-gui.jar"
set "MAIN_CLASS=org.logargos.gui.SwingLogAnalyzerApplication"

if exist "%DEST_DIR%" rmdir /s /q "%DEST_DIR%"
mkdir "%DEST_DIR%" || exit /b 1

rem NOTE: For GUI apps, we simply omit --win-console (so no console window is shown).
"%JPACKAGE_EXE%" ^
  --type app-image ^
  --name "%APP_NAME%" ^
  --input "%INPUT_DIR%" ^
  --main-jar "%MAIN_JAR%" ^
  --main-class "%MAIN_CLASS%" ^
  --dest "%DEST_DIR%"

set "JP_ERR=%ERRORLEVEL%"
if not "%JP_ERR%"=="0" (
  echo ERROR: jpackage fallo con exit code %JP_ERR%
  exit /b %JP_ERR%
)

echo.
echo OK: Generado en %DEST_DIR%\%APP_NAME%
echo Ejecutable: %DEST_DIR%\%APP_NAME%\%APP_NAME%.exe