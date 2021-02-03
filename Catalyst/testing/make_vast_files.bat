@ECHO off
ECHO Compiling test programs...
SET VALE_COMPILER=C:\Users\theow\Desktop\Thesis\ValeCompiler-0.1.0.0-Win

FOR /f "tokens=*" %%A IN ('dir /b /ad') DO IF NOT EXIST %cd%\%%A\out\main.exe CALL python3 %VALE_COMPILER%\valec.py %cd%\%%A\main.vale --gen-heap --region-override resilient-v3 --output-dir %cd%\%%A\out --elide-checks-for-known-live --print-mem-overhead

del *.obj