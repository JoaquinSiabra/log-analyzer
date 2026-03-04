@echo off
REM Run Maven for this project using a chosen Maven installation and the project-local no-mirror settings.
REM The script will try (in order): %MAVEN_HOME% if set, common extraction paths under C:\Softdesarrollo\apache-maven-3.9.x, then 'mvn' on PATH.

setlocal

REM --- Project-local Java configuration ----------------------------------------------------
REM Force the project to use the provided JDK installation (project-local only)
set "PROJECT_JAVA_HOME=C:\Program Files\Java\jdk-21.0.1"
if exist "%PROJECT_JAVA_HOME%\bin\java.exe" (
  set "JAVA_HOME=%PROJECT_JAVA_HOME%"
  set "PATH=%JAVA_HOME%\bin;%PATH%"
) else (
  echo Warning: project JDK not found at %PROJECT_JAVA_HOME%. Falling back to system JAVA_HOME or PATH java.
)
REM ----------------------------------------------------------------------------------------

REM Default candidate roots to check
set DEFAULT_ROOT=C:\Softdesarrollo\apache-maven-3.9.x

REM If the user has MAVEN_HOME set, prefer it
if defined MAVEN_HOME (
  set CANDIDATE_1=%MAVEN_HOME%
) else (
  set CANDIDATE_1=
)

set CANDIDATE_2=%DEFAULT_ROOT%\apache-maven-3.9.0
set CANDIDATE_3=%DEFAULT_ROOT%

set MAVEN_CMD=

REM Helper to check for executables
for %%R in ("%CANDIDATE_1%" "%CANDIDATE_2%" "%CANDIDATE_3%") do (
  if not "%%~R"=="" (
    if exist "%%~R\bin\mvn.cmd" set MAVEN_CMD=%%~R\bin\mvn.cmd
    if exist "%%~R\bin\mvn.bat" set MAVEN_CMD=%%~R\bin\mvn.bat
    if exist "%%~R\bin\mvn.exe" set MAVEN_CMD=%%~R\bin\mvn.exe
    if exist "%%~R\bin\mvn" set MAVEN_CMD=%%~R\bin\mvn
  )
  if defined MAVEN_CMD goto :found
)

REM Fallback: use mvn from PATH
where mvn >nul 2>nul
if %ERRORLEVEL%==0 (
  set MAVEN_CMD=mvn
)

:found
if not defined MAVEN_CMD (
  echo No Maven executable found. Please set MAVEN_HOME or edit this script to point to your Maven installation.
  exit /b 1
)

REM Run mvn with project-local settings to avoid corporate mirrors
"%MAVEN_CMD%" -s "%~dp0.mvn\no-mirror-settings.xml" %*
endlocal