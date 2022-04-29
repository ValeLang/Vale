SETLOCAL ENABLEDELAYEDEXPANSION

if "%5" == "" ( echo "Invalid version file" && exit /b 1 )
if exist "%5" (
  set VALEC_VERSION=
  for /f %%a in (%5) do (
    set VALEC_VERSION=!VALEC_VERSION!%%a
  )
  if "!VALEC_VERSION!" == "" (
    echo "Invalid version"
    exit /b 1
  )
  echo "Version: !VALEC_VERSION!"
) else (
  echo "Version file doesn't exist!"
  exit /b 1
)


cd Backend

echo Generating Backend...
cmake -B build -D LLVM_DIR="%1\build\lib\cmake\llvm" || echo "Backend generate failed, aborting." && exit /b 1

echo Compiling Backend...
cmake --build build || echo "Backend build failed, aborting." && exit /b 1


cd ..\Frontend

echo Compiling Frontend...
call sbt assembly || echo "Frontend build failed, aborting." && exit /b 1


cd ..\Coordinator

echo Compiling Coordinator...
call build.bat %2 || echo "Coordinator build failed, aborting." && exit /b 1



cd ..\scripts



if exist "..\release-windows" rmdir /S /Q "..\release-windows"
mkdir "..\release-windows"
rem mkdir "..\release-windows\samples"
copy ..\Frontend\Frontend.jar ..\release-windows\Frontend.jar
rem echo d | xcopy /s /e /y ..\Frontend\Tests\test\main\resources\programs ..\release-windows\samples
echo d | xcopy /s /e /y ..\Backend\builtins ..\release-windows\builtins
copy ..\Backend\build\Debug\backend.exe ..\release-windows\backend.exe
echo d | xcopy /s /e /y ..\stdlib ..\release-windows\stdlib
copy ..\Coordinator\build\valec.exe ..\release-windows\valec.exe

copy all\README ..\release-windows\README.txt
copy all\valec-help-build.txt ..\release-windows\valec-help-build.txt
copy all\valec-help.txt ..\release-windows\valec-help.txt
copy all\valec-version.txt ..\release-windows\valec-version.txt
rem echo d | xcopy /s /e /y all\helloworld ..\release-windows\samples\helloworld

copy %1\bin\LLVM-C.dll ..\release-windows\LLVM-C.dll

cd ..\release-windows
PATH=%PATH%;C:\Program Files\7-Zip
PATH=%PATH%;C:\Program Files\7-Zip\7z.exe
7z a Vale-Windows-0.zip *




cd ..\Tester


mkdir .\BuiltValeCompiler
tar -xf ..\release-windows\Vale-Windows-0.zip -C .\BuiltValeCompiler


echo Compiling Tester...
call build.bat %2 || echo "Tester build failed, aborting." && exit /b 1

echo Running Tester...
build\testvalec --frontend_path .\BuiltValeCompiler\Frontend.jar --backend_path .\BuiltValeCompiler\backend.exe --builtins_dir .\BuiltValeCompiler\builtins --valec_path .\BuiltValeCompiler\valec.exe --backend_tests_dir ..\Backend\test --frontend_tests_dir ..\Frontend --concurrent 6 @assist || echo "Tests failed, aborting." && exit /b 1

cd ..
