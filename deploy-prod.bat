@echo off
setlocal

rem === CONFIG ===
set "WAR_NAME=jnodesWeb.war"
set "PROJECT_DIR=%~dp0"
set "WAR_PATH=%PROJECT_DIR%target\%WAR_NAME%"

rem Remote host settings
set "REMOTE_USER=root"            rem change to your SSH user
set "REMOTE_HOST=192.168.192.71"   rem change to your host/ip
set "REMOTE_PORT=22"
set "REMOTE_PATH=/opt/tomcat/webapps"
set "SSH_KEY="                           rem leave empty to use password prompt; set to key path if needed
set "TOMCAT_SERVICE=tomcat"              rem systemd service name (e.g., tomcat, tomcat9, tomcat10)

rem Prefer Windows built-in OpenSSH if present
set "SSH_CMD=%SystemRoot%\System32\OpenSSH\ssh.exe"
if not exist "%SSH_CMD%" set "SSH_CMD=ssh"

rem === Build WAR (always rebuild) ===
echo Building WAR...
pushd "%PROJECT_DIR%" || exit /b 1
call mvn -U clean package || (
  echo Maven build failed.
  popd
  exit /b 1
)
popd

if not exist "%WAR_PATH%" (
  echo WAR not found at %WAR_PATH% after build.
  exit /b 1
)

for %%t in ("%SSH_CMD%") do (
  if not exist "%%~fxt" (
    rem if it is not an absolute path, trust PATH lookup
    where %%~nxt >nul 2>&1 || (
      echo Required tool not found: %%~nxt
      exit /b 1
    )
  )
)

if "%REMOTE_HOST%"=="" (
  echo REMOTE_HOST is not set. Please edit deploy-prod.bat.
  exit /b 1
)
if "%REMOTE_USER%"=="" (
  echo REMOTE_USER is not set. Please edit deploy-prod.bat.
  exit /b 1
)

set "SSH_PORT_ARG=-p %REMOTE_PORT%"
if "%REMOTE_PORT%"=="" (
  set "SSH_PORT_ARG="
)

set "SSH_KEY_ARG="
if not "%SSH_KEY%"=="" set "SSH_KEY_ARG=-i \"%SSH_KEY%\""

set "REMOTE_TARGET=%REMOTE_PATH%/%WAR_NAME%"

echo Uploading %WAR_NAME% and restarting Tomcat in one SSH session...
%SSH_CMD% %SSH_PORT_ARG% %SSH_KEY_ARG% %REMOTE_USER%@%REMOTE_HOST% "cat > %REMOTE_TARGET% && sudo systemctl restart %TOMCAT_SERVICE%" < "%WAR_PATH%"
if errorlevel 1 (
  echo Remote upload/restart failed.
  exit /b 1
)

echo Deployment complete.
endlocal
