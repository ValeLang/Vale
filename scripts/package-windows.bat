if exist "..\release-windows" rmdir /S /Q "..\release-windows"
mkdir "..\release-windows"
mkdir "..\release-windows\samples"
copy ..\Valestrom\Valestrom.jar ..\release-windows\Valestrom.jar

echo d | xcopy /s /e /y ..\Valestrom\Samples\test\main\resources\programs ..\release-windows\samples
echo d | xcopy /s /e /y ..\Valestrom\Samples\test\main\resources\libraries ..\release-windows\samples\libraries
echo d | xcopy /s /e /y ..\Midas\src\builtins ..\release-windows\builtins
echo d | xcopy /s /e /y ..\Midas\vstl ..\release-windows\vstl
copy ..\Midas\valec.py ..\release-windows\valec.py
copy releaseREADME.txt ..\release-windows\README.txt
copy valec-help* ..\release-windows
copy ..\Midas\build\Debug\valec.exe ..\release-windows\valec.exe
copy ..\Midas\LLVM-C.dll ..\release-windows\LLVM-C.dll