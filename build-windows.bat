
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

cd ..\Tester

echo Compiling Tester...
call build.bat %2

echo Running Tester...
build\tester --valestrom_dir_override ..\Valestrom --midas_dir_override ..\Midas\build\Debug --builtins_dir_override ..\Midas\src\builtins --valec_dir_override ..\Driver\build --midas_tests_dir ..\Midas\test --concurrent 6 @assist

cd ..\scripts

call package-windows.bat
