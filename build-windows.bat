
powershell -c "$ProgressPreference = 'SilentlyContinue' ; Invoke-WebRequest -Uri 'https://vale.dev/releases/ValeCompiler-0.1.3.3-Win.zip' -OutFile '%temp%\ValeCompiler-0.1.3.3-Win.zip'"
mkdir %2
tar xf "%temp%\ValeCompiler-0.1.3.3-Win.zip" -C %2

cd Valestrom

echo Compiling Valestrom...
call sbt assembly

cd ..

cd Midas

echo Generating Midas...
cmake -B build -D LLVM_DIR="%1\lib\cmake\llvm"

cd build

echo Compiling Midas...
cmake --build .




cd ..\..\Driver

echo Compiling Driver...
call build.bat %2



cd ..\scripts

call package-windows.bat



cd ..\Tester


mkdir ./BuiltValeCompiler
tar -xf ../release-unix/ValeCompiler.zip -C ./BuiltValeCompiler


echo Compiling Tester...
call build.bat %2

echo Running Tester...
build\tester --valestrom_path .\BuiltValeCompiler\Valestrom.jar --midas_path .\BuiltValeCompiler\midas --builtins_dir .\BuiltValeCompiler\builtins --valec_path .\BuiltValeCompiler\valec --midas_tests_dir ..\Midas\test --valestrom_tests_dir ..\Valestrom --concurrent 6 @assist
