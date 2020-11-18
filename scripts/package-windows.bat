if exist "..\release-windows" rmdir /S "..\release-windows"
mkdir "..\release-windows"
copy ..\Valestrom.jar ..\release-windows\Valestrom.jar


xcopy /s /e /y ..\Valestrom\Samples\test\main\resources\libraries ..\release-windows\vstl
copy ..\Midas\vstl\* ..\release-windows\vstl
xcopy /s /e /y ..\Valestrom\Samples\test\main\resources\programs ..\release-windows\samples
xcopy /s /e /y ..\benchmarks\BenchmarkRL\vale ..\release-windows\benchmark
xcopy /s /e /y ..\Midas\src\valestd ..\release-windows\runtime
copy releaseREADME.txt ..\release-windows\README.txt
copy ..\Midas\x64\Release\Midas.exe ..\release-windows\Midas.exe
copy ..\Midas\valec.py ..\release-windows\valec.py
copy ..\Midas\x64\Release\LLVM-C.dll ..\release-windows
