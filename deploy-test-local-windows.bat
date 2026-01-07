@echo off
setlocal

rem === Resolve Java ===
if defined JAVA_HOME (
  if not exist "%JAVA_HOME%\bin\java.exe" (
    echo JAVA_HOME is set but invalid: %JAVA_HOME%
    set "JAVA_HOME="
  )
)

if not defined JAVA_HOME (
  for /f "delims=" %%J in ('where java 2^>nul') do (
    if not defined JAVA_HOME (
      for %%K in ("%%~dpJ..") do set "JAVA_HOME=%%~fK"
    )
  )
)

if not defined JAVA_HOME (
  for %%P in (
    "C:\Program Files\Java\jdk-21"
    "C:\Program Files\Java\jdk-17"
    "C:\Program Files\Eclipse Adoptium\jdk-21"
    "C:\Program Files\Eclipse Adoptium\jdk-17"
  ) do (
    if not defined JAVA_HOME if exist "%%~P\bin\java.exe" set "JAVA_HOME=%%~P"
  )
)

if not defined JAVA_HOME (
  echo JAVA_HOME not found. Set JAVA_HOME to your JDK install and rerun.
  exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo JAVA_HOME is set but java.exe not found: %JAVA_HOME%
  exit /b 1
)

set "JRE_HOME=%JAVA_HOME%"
echo Using JAVA_HOME=%JAVA_HOME%

rem === Tomcat locations ===
set "CATALINA_HOME=C:\Code\Java\apache-tomcat-11.0.15-windows-x64\apache-tomcat-11.0.15" rem modify to your local tomcat11 folder as neded
set "CATALINA_BASE=%CATALINA_HOME%"

if not exist "%CATALINA_HOME%\bin\startup.bat" (
  echo Tomcat not found at %CATALINA_HOME%
  exit /b 1
)

set "PROJECT_DIR=%~dp0"
set "WAR_NAME=jnodesWeb.war"
set "WAR_PATH=%PROJECT_DIR%target\%WAR_NAME%"
set "WEBAPPS_DIR=%CATALINA_HOME%\webapps"

rem === Build WAR ===
echo Building WAR...
pushd "%PROJECT_DIR%" || exit /b 1
call mvn -U clean package || (
  echo Maven build failed.
  popd
  exit /b 1
)
popd

if not exist "%WAR_PATH%" (
  echo WAR not found at %WAR_PATH%
  exit /b 1
)

echo Build finished: %WAR_PATH%

rem === Deploy WAR ===
echo Deploying WAR to %WEBAPPS_DIR%...
if not exist "%WEBAPPS_DIR%" (
  echo Webapps dir not found: %WEBAPPS_DIR%
  exit /b 1
)
copy /Y "%WAR_PATH%" "%WEBAPPS_DIR%\%WAR_NAME%" >nul || (
  echo Copy failed.
  exit /b 1
)
echo Copy complete.

rem === Start Tomcat ===
echo Starting Tomcat from %CATALINA_HOME%...
call "%CATALINA_HOME%\bin\startup.bat" || (
  echo Tomcat startup failed.
  exit /b 1
)
echo Tomcat start script invoked. Check logs in %CATALINA_BASE%\logs\ for status.

echo Done. Visit http://localhost:8080/jnodesWeb/
endlocal
