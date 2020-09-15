if not exist "..\release-windows" mkdir "..\release-windows"
copy ..\Valestrom\out\artifacts\Driver_jar\Driver.jar ..\release-windows\Driver.jar
xcopy /s /e /y ..\Valestrom\Samples\test\main\resources\libraries ..\release-windows\vstl
xcopy /s /e /y ..\Valestrom\Samples\test\main\resources\programs ..\release-windows\samples
xcopy /s /e /y ..\benchmarks\BenchmarkRL\vale ..\release-windows\benchmark
xcopy /s /e /y ..\Midas\src\valestd ..\release-windows\runtime
copy releaseREADME.txt ..\release-windows\README.txt
copy ..\Midas\x64\Debug\Midas.exe ..\release-windows\Midas.exe
copy ..\Midas\valec.py ..\release-windows\valec.py
copy ..\Midas\x64\Debug\LLVM-C.dll ..\release-windows
