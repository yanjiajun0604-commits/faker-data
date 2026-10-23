@echo off
setlocal
pushd "%~dp0"
if not exist "target\faker-data-0.1.0.jar" (
  call mvnw.cmd -B -DskipTests package
  if errorlevel 1 (
    popd
    exit /b 1
  )
)
echo Open http://127.0.0.1:8085/faker/ ^(or your SERVER_PORT^)
java -jar "target\faker-data-0.1.0.jar" %*
set "FAKER_EXIT=%ERRORLEVEL%"
popd
exit /b %FAKER_EXIT%
