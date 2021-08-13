if exist "..\release-windows" rmdir /S /Q "..\release-windows"
mkdir "..\release-windows"
mkdir "..\release-windows\samples"
copy ..\Valestrom\Valestrom.jar ..\release-windows\Valestrom.jar

echo d | xcopy /s /e /y ..\Valestrom\Tests\test\main\resources\programs ..\release-windows\samples
echo d | xcopy /s /e /y ..\Midas\src\builtins ..\release-windows\builtins
copy releaseREADME.txt ..\release-windows\README.txt
copy valec* ..\release-windows
copy ..\Midas\build\Debug\midas.exe ..\release-windows\midas.exe
git clone https://github.com/ValeLang/stdlib ../release-windows/stdlib
echo d | xcopy /s /e /y helloworld ..\release-windows\samples\helloworld
copy ..\Driver\build\valec ..\release-windows\valec

copy %1\bin\LLVM-C.dll ..\release-windows\LLVM-C.dll
