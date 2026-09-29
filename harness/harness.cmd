@echo off
rem Golem harness CLI wrapper (Windows cmd / PowerShell). See README.md next to this file.
rem Builds build\libs\golem-harness.jar on first use, then runs it on a Java 21+ runtime.
rem Prefer the POSIX wrapper `./harness` from Git Bash: cmd mangles quotes in JSON arguments (`exec`, `query`).
rem After changing the CLI sources from cmd, delete build\libs\golem-harness.jar to force a rebuild.
setlocal
set "HERE=%~dp0"
set "JAR=%HERE%build\libs\golem-harness.jar"
if not exist "%JAR%" (
    echo [harness] building harness jar...
    call "%HERE%gradlew.bat" -q jar || exit /b 1
)
set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
for /d %%D in ("%USERPROFILE%\.gradle\jdks\*21*") do if exist "%%~D\bin\java.exe" set "JAVA_EXE=%%~D\bin\java.exe"
"%JAVA_EXE%" -jar "%JAR%" %*
exit /b %ERRORLEVEL%
