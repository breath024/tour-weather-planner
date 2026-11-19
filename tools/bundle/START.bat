@echo off
rem TourWeather run bundle (Windows). ASCII only on purpose: messages live in tomcat\msg\*.txt
setlocal
set "HERE=%~dp0"
set "URL=http://127.0.0.1:8095/TourWeather/"
if "%~1"==":open" goto :open
chcp 65001 >nul
title TourWeather - close this window to stop
netstat -ano | findstr /r /c:":8095 .*LISTENING" >nul
if not errorlevel 1 (
  type "%HERE%tomcat\msg\msg_port.txt"
  pause
  exit /b 1
)
type "%HERE%tomcat\msg\msg_start.txt"
set "JAVA_HOME="
set "JRE_HOME=%HERE%jre"
set "CATALINA_HOME=%HERE%tomcat"
set "CATALINA_BASE=%HERE%tomcat"
set "CATALINA_OPTS=-Xmx256m -Dfile.encoding=UTF-8"
if not defined TW_NOBROWSER start "" /b cmd /c call "%~f0" :open
call "%CATALINA_HOME%\bin\catalina.bat" run
exit /b

:open
where curl.exe >nul 2>nul || (ping -n 9 127.0.0.1 >nul & start "" "%URL%" & exit /b)
set /a n=0
:wait
ping -n 2 127.0.0.1 >nul
curl.exe -s -o nul "%URL%" && goto :go
set /a n+=1
if %n% lss 90 goto :wait
exit /b
:go
start "" "%URL%"
exit /b
