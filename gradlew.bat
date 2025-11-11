@ECHO OFF
@REM Gradle startup script for Windows (Gradle 8.9 wrapper)

SETLOCAL

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0

set CLASSPATH=%DIRNAME%gradle\wrapper\gradle-wrapper.jar

set JAVA_EXE=java.exe
if defined JAVA_HOME set JAVA_EXE=%JAVA_HOME%\bin\java.exe

if exist "%JAVA_EXE%" goto OkJHome

echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
goto fail

:OkJHome
"%JAVA_EXE%" -classpath "%CLASSPATH%" -Dorg.gradle.appname=%APP_BASE_NAME% org.gradle.wrapper.GradleWrapperMain %*
goto end

:fail
exit /b 1

:end
ENDLOCAL
