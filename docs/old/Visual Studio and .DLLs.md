Here\'s how to set up the Midas project in Visual Studio. Assuming you
already have a LLVM visual studio project that built successfully.

Install visual studio, and make sure you have its windows SDK installed.
Otherwise youll hit a missing kernel32.lib error when you try and link a
dll.

Made sure to target x64. Did this for a hello world app before bringing
in anything LLVMish.

Copy the Directory.Build.props.template to Directory.Build.props, and
change the paths inside to point to the correct LLVM paths.

Note how we\'re pointing at two different LLVM directories. One is the
original source, and one is the compiled LLVM. We have to use the two in
tandem; files in one will look for files in the other.

In VC++ directories:

\- Added to the include directories:

\- \$(LLVMProjectDir)\\llvm\\include

\- \$(LLVMVSProjDir)\\include

\- Added to the library directories: \$(LLVMBuiltDir)\\lib

In Linker / Additional dependencies, added every single lib file from my
D:\\llvm-project\\vsbuild\\Release\\lib

(perhaps we can use less?)

Added post-build event: xcopy \"\$(LLVMBuiltDir)\\bin\\\*.dll\"
\"\$(TargetDir)\" /F /R /Y /I

(might be able to get away with just LLVM-C.dll)

Configuration Properties -\> C/C++ -\> Language -\> C++ Language
Standard to C++17

(should we add a step here to output valec.exe rather than Midas.exe?)

You need to open up a console now. it can probably just be a regular
one.

python ..\\ValeCompiler\\valec.py build Spaceship.vale

will generate a build.obj file with our vale program.

clang message.c -c -o message.obj

will make our intermediary C into an obj.

example:

> \_\_declspec(dllexport) void Step(int64_t\* bufferLength, uint8_t\*
> buffer) {
>
> Spaceship\* spaceship = makeSpaceship();
>
> void\* messageBegin = ValeMessageBegin(spaceship);
>
> uint64_t messageSize = \*(uint64_t\*)messageBegin;
>
> memcpy(buffer, messageBegin, messageSize);
>
> \*bufferLength = messageSize;
>
> ValeReleaseMessage(spaceship);
>
> }
>
> int8_t DllMain() {
>
> return 1;
>
> }

clang -shared -v -o message.dll message.obj build.obj strings.obj
stdio.obj

will combine them all into a .dll

If it doesnt generate a lib, make sure you actually exported stuff. In
C++ it needs extern \"C\" { } and \_\_declspec(dllexport), and in LLVM
it needs:

LLVMSetDLLStorageClass(entryFunctionL, LLVMDLLExportStorageClass);

LLVMSetFunctionCallConv(entryFunctionL, LLVMX86StdcallCallConv);

// might not need this one actually:

LLVMSetLinkage(entryFunctionL, LLVMDLLExportLinkage);

Can use these commands to peer inside them:

dumpbin /exports D:\\test.lib

dumpbin /exports D:\\test.dll

If theres no functions actually listed there, then check the dllexport
stuff above.

Can import them using \_\_declspec(dllimport) like a regular DLL.

drag it into unity, and add some extern stuff, like:

> public static class DllFunctions {
>
> \[DllImport(\"test.dll\")\]
>
> public static extern int dllmain(int n, IntPtr p);
>
> }

note that unity doesnt reload DLLs. would need something like
[[https://www.forrestthewoods.com/blog/how-to-reload-native-plugins-in-unity/]{.underline}](https://www.forrestthewoods.com/blog/how-to-reload-native-plugins-in-unity/)
for that.
