

cd Valestrom

echo Compiling Valestrom...
call sbt assembly || echo "Valestrom build failed, aborting." && exit /b 1

cd ..

cd Midas

echo Generating Midas...
cmake -B build -D LLVM_DIR="%1\lib\cmake\llvm" || echo "Midas generate failed, aborting." && exit /b 1

cd build

echo Compiling Midas...
cmake --build . || echo "Midas build failed, aborting." && exit /b 1




cd ..\..\Driver

echo Compiling Driver...
call build.bat %2 || echo "Driver build failed, aborting." && exit /b 1



cd ..\scripts





if exist "..\release-windows" rmdir /S /Q "..\release-windows"
mkdir "..\release-windows"
mkdir "..\release-windows\samples"
copy ..\Valestrom\Valestrom.jar ..\release-windows\Valestrom.jar

echo d | xcopy /s /e /y ..\Valestrom\Tests\test\main\resources\programs ..\release-windows\samples
echo d | xcopy /s /e /y ..\Midas\src\builtins ..\release-windows\builtins
copy releaseREADME.txt ..\release-windows\README.txt
copy valec* ..\release-windows
copy ..\Midas\build\Debug\midas.exe ..\release-windows\midas.exe
echo d | xcopy /s /e /y %3 ..\release-windows\stdlib
echo d | xcopy /s /e /y helloworld ..\release-windows\samples\helloworld
copy ..\Driver\build\valec.exe ..\release-windows\valec.exe

copy %1\bin\LLVM-C.dll ..\release-windows\LLVM-C.dll

cd ..\release-windows

PATH=%PATH%;C:\Program Files\7-Zip
PATH=%PATH%;C:\Program Files\7-Zip\7z.exe
7z a ValeCompiler.zip *




cd ..\Tester


mkdir .\BuiltValeCompiler
tar -xf ..\release-windows\ValeCompiler.zip -C .\BuiltValeCompiler


echo Compiling Tester...
call build.bat %2 || echo "Tester build failed, aborting." && exit /b 1

echo Running Tester...
build\testvalec --valestrom_path .\BuiltValeCompiler\Valestrom.jar --midas_path .\BuiltValeCompiler\midas.exe --builtins_dir .\BuiltValeCompiler\builtins --valec_path .\BuiltValeCompiler\valec.exe --midas_tests_dir ..\Midas\test --valestrom_tests_dir ..\Valestrom --concurrent 6 @assist || echo "Tests failed, aborting." && exit /b 1
