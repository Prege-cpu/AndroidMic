@rem
@rem Gradle startup script for Windows
@rem
@if "%DEBUG%" == "" @echo off
set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_HOME=%DIRNAME%
if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
goto execute
:findJavaFromJavaHome
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
:execute
gradle %*
if %ERRORLEVEL% EQU 0 goto end
"%JAVA_EXE%" -Dorg.gradle.appname=gradlew -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
:end
