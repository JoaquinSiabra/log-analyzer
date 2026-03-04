@echo off
REM Helper to run the project Maven Wrapper using the project JDK 21 without modifying system settings.
REM Usage: mvnw-jdk21.cmd [args]

setlocal
set "PROJECT_JAVA_HOME=C:\Program Files\Java\jdk-21.0.1"
if exist "%PROJECT_JAVA_HOME%\bin\java.exe" (
  set "JAVA_HOME=%PROJECT_JAVA_HOME%"
  set "PATH=%JAVA_HOME%\bin;%PATH%"
) else (
  echo Warning: project JDK not found at %PROJECT_JAVA_HOME%. Falling back to system JAVA_HOME or PATH java.
)

REM Delegate to the normal mvnw wrapper in the project
call "%~dp0mvnw.cmd" %*
endlocal
