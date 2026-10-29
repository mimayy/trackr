@REM ---------------------------------------------------------------------------
@REM Maven Wrapper (script-only), Windows.
@REM
@REM Downloads the Maven distribution named in
@REM .mvn\wrapper\maven-wrapper.properties into %USERPROFILE%\.m2\wrapper and
@REM runs it, so this repository needs no checked-in jar.
@REM
@REM If Maven is already installed you can simply use `mvn` instead.
@REM ---------------------------------------------------------------------------
@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "PROPERTIES=%SCRIPT_DIR%.mvn\wrapper\maven-wrapper.properties"

if not exist "%PROPERTIES%" (
  echo Cannot find %PROPERTIES% 1>&2
  exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in ("%PROPERTIES%") do (
  if "%%A"=="distributionUrl" set "DISTRIBUTION_URL=%%B"
)

if not defined DISTRIBUTION_URL (
  echo distributionUrl is not set in %PROPERTIES% 1>&2
  exit /b 1
)

for %%F in ("%DISTRIBUTION_URL%") do set "ARCHIVE_NAME=%%~nxF"
for %%F in ("%DISTRIBUTION_URL%") do set "MAVEN_DIR_NAME=%%~nF"
set "MAVEN_DIR_NAME=%MAVEN_DIR_NAME:-bin=%"

set "WRAPPER_HOME=%USERPROFILE%\.m2\wrapper\dists\%MAVEN_DIR_NAME%"
set "MAVEN_HOME=%WRAPPER_HOME%\%MAVEN_DIR_NAME%"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  echo Downloading Maven from %DISTRIBUTION_URL% ... 1>&2
  if not exist "%WRAPPER_HOME%" mkdir "%WRAPPER_HOME%"

  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ProgressPreference='SilentlyContinue';" ^
    "Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%WRAPPER_HOME%\%ARCHIVE_NAME%';" ^
    "Expand-Archive -Path '%WRAPPER_HOME%\%ARCHIVE_NAME%' -DestinationPath '%WRAPPER_HOME%' -Force;" ^
    "Remove-Item '%WRAPPER_HOME%\%ARCHIVE_NAME%' -Force"

  if errorlevel 1 (
    echo Could not download Maven. Install Maven manually and use 'mvn'. 1>&2
    exit /b 1
  )
)

set "MAVEN_PROJECTBASEDIR=%SCRIPT_DIR%"
call "%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
