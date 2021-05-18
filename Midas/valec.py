import unittest
import subprocess
import os.path
import os
import sys
import shutil
import glob
from os.path import abspath
import argparse
import platform
import os.path

from pathlib import *
from os import path
from subprocess import PIPE
from typing import Dict, Any, List, Callable, Optional


def procrun(args: List[str], **kwargs) -> subprocess.CompletedProcess:
    print("Running: " + " ".join(args))
    return subprocess.run(args, stdout=PIPE, stderr=PIPE, text=True, **kwargs)


class ValeCompiler:
    def valestrom(self,
                  command: str,
                  user_modules: List[str],
                  user_valestrom_inputs: List[Path],
                  valestrom_options: List[str]) -> subprocess.CompletedProcess:

        valestrom_inputs = user_valestrom_inputs

        if self.build_dir != Path("."):
            if os.path.exists(self.build_dir):
                shutil.rmtree(self.build_dir)
            os.makedirs(self.build_dir)

        valestrom_options.append("--output-dir")
        valestrom_options.append(str(self.build_dir))

        if self.parseds_output_dir != None:
            valestrom_options.append("-op")
            valestrom_options.append(str(self.parseds_output_dir))

        return procrun(
            [
                "java",
                "-cp",
                str(self.valestrom_path / "Valestrom.jar"),
                "net.verdagon.vale.driver.Driver",
                command
            ] + user_modules + valestrom_options + list((x[0] + ":" + str(x[1])) for x in valestrom_inputs)
        )

    def valec(self,
              vast_file: Path,
              o_files_dir: str,
              midas_options: List[str]) -> subprocess.CompletedProcess:
        return procrun(
            [str(self.valec_path), "--verify", "--output-dir", o_files_dir, str(vast_file)] + midas_options)

    def clang(self,
              o_files: List[Path],
              o_files_dir: Path,
              exe_file: Path,
              include_path: Optional[Path]) -> subprocess.CompletedProcess:
        if self.windows:
            args = [
                "cl.exe", '/ENTRY:"main"', '/SUBSYSTEM:CONSOLE', "/Fe:" + str(exe_file),
                "/fsanitize=address", "clang_rt.asan_dynamic-x86_64.lib", "clang_rt.asan_dynamic_runtime_thunk-x86_64.lib"
            ] + list(str(x) for x in o_files)
            if include_path is not None:
                args.append("-I" + str(include_path))
            return procrun(args)
        else:
            clang = "clang-11" if shutil.which("clang-11") is not None else "clang"
            # "-fsanitize=address", "-fno-omit-frame-pointer", "-g",
            args = [clang, "-O3", "-lm", "-o", str(exe_file)] + list(str(x) for x in o_files)
            if include_path is not None:
                args.append("-I" + str(include_path))
            return procrun(args)

    def compile_and_execute(
        self, args: str) -> subprocess.CompletedProcess:


        cwd = Path(os.path.dirname(os.path.realpath(__file__)))

        if len(os.environ.get('VALESTROM_PATH', '')) > 0:
            self.valestrom_path = Path(os.environ.get('VALESTROM_PATH', ''))
        elif path.exists(cwd / "Valestrom.jar"):
            self.valestrom_path = cwd
        elif path.exists(cwd / "test/Valestrom.jar"):
            self.valestrom_path = cwd / "test"
        elif path.exists(cwd / "../Valestrom/Valestrom.jar"):
            self.valestrom_path = cwd / "../Valestrom"
        elif path.exists(cwd / "../Valestrom/out/artifacts/Valestrom_jar/Valestrom.jar"):
            self.valestrom_path = cwd / "../Valestrom/out/artifacts/Valestrom_jar"
        else:
            self.valestrom_path = cwd

        if len(os.environ.get('VALESTD_PATH', '')) > 0:
            self.builtins_path = Path(os.environ.get('VALESTD_PATH', ''))
        elif path.exists(cwd / "src/builtins"):
            self.builtins_path = cwd / "src/builtins"
        elif path.exists(cwd / "builtins"):
            self.builtins_path = cwd / "builtins"
        else:
            self.builtins_path = cwd

        # Maybe we can add a command line param here too, relying on environments is always irksome.
        self.valec_path: Path = cwd
        if len(os.environ.get('VALEC_PATH', '')) > 0:
            print(f"Using valec at {self.valec_path}. ", file=sys.stderr)
            self.valec_path = Path(os.environ.get('VALEC_PATH', ''))
        elif shutil.which("valec") != None:
            self.valec_path = Path(shutil.which("valec"))
        elif path.exists(cwd / "valec"):
            self.valec_path = cwd / "valec"
        elif path.exists(cwd / "valec.exe"):
            self.valec_path = cwd / "valec.exe"
        elif path.exists(cwd / "cmake-build-debug/valec"):
            self.valec_path = cwd / "cmake-build-debug/valec"
        elif path.exists(cwd / "build/valec"):
            self.valec_path = cwd / "build/valec"
        elif path.exists(cwd / "build/valec.exe"):
            self.valec_path = cwd / "build/valec.exe"
        elif path.exists(cwd / "build/Debug/valec.exe"):
            self.valec_path = cwd / "build/Debug/valec.exe"
        elif path.exists(cwd / "build/Release/valec.exe"):
            self.valec_path = cwd / "build/Release/valec.exe"
        elif path.exists(cwd / "x64/Debug/valec.exe"):
            self.valec_path = cwd / "x64/Debug/valec.exe"
        elif path.exists(cwd / "x64/Release/valec.exe"):
            self.valec_path = cwd / "x64/Release/valec.exe"
        else:
            print("No VALEC_PATH in env, and couldn't find one nearby, aborting!", file=sys.stderr)
            sys.exit(1)

        self.windows = platform.system() == 'Windows'

        self.vs_path: str = ''
        if self.windows:
            self.vs_path = os.environ.get('VCInstallDir', '')
            if len(self.vs_path) == 0:
                print('No VCInstallDir in env! To fix:', file=sys.stderr)
                print('1. Make sure Visual Studio is installed.', file=sys.stderr)
                print('2. Run vcvars64.bat. Example location: C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Community\\VC\\Auxiliary\\Build\\vcvars64.bat', file=sys.stderr)
                print('3. Run `echo %%VCInstallDir%%` to verify', file=sys.stderr)
                sys.exit(1)
            print(f"Using Visual Studio at {self.vs_path}. ", file=sys.stderr)
        else:
            pass



        # parser = argparse.ArgumentParser(description='Compiles a Vale program.')
        # parser.add_argument('integers', metavar='N', type=int, nargs='+',
        #                     help='an integer for the accumulator')
        # parser.add_argument('--sum', dest='accumulate', action='store_const',
        #                     const=sum, default=max,
        #                     help='sum the integers (default: find the max)')
        # parser.add_argument('--sum', dest='accumulate', action='store_const',
        #                     const=sum, default=max,
        #                     help='sum the integers (default: find the max)')
        # args = parser.parse_args()

        self.build_dir = Path(f".")
        exports_dir = Path(f".")
        exe_file = ("main.exe" if self.windows else "a.out")
        self.parseds_output_dir = None
        add_exports_include_path = False



        valestrom_options = []
        midas_options = []
        if "--flares" in args:
            args.remove("--flares")
            midas_options.append("--flares")
        if "--benchmark" in args:
            args.remove("--benchmark")
            valestrom_options.append("--benchmark")
        if "--gen-heap" in args:
            args.remove("--gen-heap")
            midas_options.append("--gen-heap")
        if "--census" in args:
            args.remove("--census")
            midas_options.append("--census")
        if "--print-mem-overhead" in args:
            args.remove("--print-mem-overhead")
            midas_options.append("--print-mem-overhead")
        if "--verify" in args:
            args.remove("--verify")
            midas_options.append("--verify")
        if "--verbose" in args:
            args.remove("--verbose")
            valestrom_options.append("--verbose")
        if "--include-builtins" in args:
            ind = args.index("--include-builtins")
            del args[ind]
            val = args[ind]
            del args[ind]
            valestrom_options.append("--include-builtins")
            valestrom_options.append(val)
        # if "--output-vpst" in args:
        #     args.remove("--output-vpst")
        #     valestrom_options.append("--output-vpst")
        if "--llvmir" in args:
            args.remove("--llvmir")
            midas_options.append("--llvmir")
        if "--elide-checks-for-known-live" in args:
            args.remove("--elide-checks-for-known-live")
            midas_options.append("--elide-checks-for-known-live")
        if "--override-known-live-true" in args:
            args.remove("--override-known-live-true")
            midas_options.append("--override-known-live-true")
        if "--region-override" in args:
            ind = args.index("--region-override")
            del args[ind]
            val = args[ind]
            del args[ind]
            midas_options.append("--region-override")
            midas_options.append(val)
        if "--cpu" in args:
            ind = args.index("--cpu")
            del args[ind]
            val = args[ind]
            del args[ind]
            midas_options.append("--cpu")
            midas_options.append(val)
        if "--output-dir" in args:
            ind = args.index("--output-dir")
            del args[ind]
            val = args[ind]
            del args[ind]
            self.build_dir = Path(val)
            midas_options.append("--output-dir")
            midas_options.append(val)
        if "--exports-dir" in args:
            ind = args.index("--exports-dir")
            del args[ind]
            val = args[ind]
            del args[ind]
            exports_dir = Path(val)
            midas_options.append("--exports-dir")
            midas_options.append(val)
        if "--output-vast" in args:
            ind = args.index("--output-vast")
            del args[ind]
            val = args[ind]
            del args[ind]
            valestrom_options.append("--output-vast")
            valestrom_options.append(val)
        if "--output-vpst" in args:
            ind = args.index("--output-vpst")
            del args[ind]
            val = args[ind]
            del args[ind]
            valestrom_options.append("--output-vpst")
            valestrom_options.append(val)
        if "--add-exports-include-path" in args:
            ind = args.index("--add-exports-include-path")
            del args[ind]
            add_exports_include_path = True
        if "-o" in args:
            ind = args.index("-o")
            del args[ind]
            val = args[ind]
            del args[ind]
            exe_file = val
        if "-op" in args:
            ind = args.index("-op")
            del args[ind]
            val = args[ind]
            del args[ind]
            parseds_output_dir = val

        if len(args) == 0:
            print("Must supply a command, such as 'help', 'build`, 'run'.")
            sys.exit(22)

        if args[0] == "help" or args[0] == "--help":
            if len(args) < 2:
                with open(str(self.valestrom_path / "valec-help.txt"), 'r') as f:
                    print(f.read())
            elif args[1] == "build":
                with open(str(self.valestrom_path / "valec-help-build.txt"), 'r') as f:
                    print(f.read())
            elif args[1] == "run":
                with open(str(self.valestrom_path / "valec-help-run.txt"), 'r') as f:
                    print(f.read())
            elif args[1] == "paths":
                print("Valestrom path: " + str(self.valestrom_path))
                print("Builtins path: " + str(self.builtins_path))
                print("valec path: " + str(self.valec_path))
            else:
                print("Unknown subcommand: " + args[1])
            sys.exit(0)
        elif args[0] == "build":
            args.pop(0)

            user_modules = []
            user_valestrom_inputs = []
            user_vast_files = []
            user_c_files = []

            for arg in args:
                if ":" in arg:
                    parts = arg.split(":")
                    if len(parts) != 2:
                        print("Unrecognized input: " + arg)
                        sys.exit(22)
                    module_name = parts[0]
                    contents_path = Path(parts[1]).expanduser()
                    print("contents path: " + str(contents_path))
                    if str(contents_path).endswith(".vale"):
                        user_valestrom_inputs.append([module_name, contents_path])
                    elif str(contents_path).endswith(".vpst"):
                        user_valestrom_inputs.append([module_name, contents_path])
                    elif str(contents_path).endswith(".vast"):
                        user_vast_files.append(contents_path)
                    elif str(contents_path).endswith(".c"):
                        user_c_files.append(contents_path)
                    elif contents_path.is_dir():
                        for c_file in contents_path.rglob('*.c'):
                            user_c_files.append(Path(c_file))
                        user_valestrom_inputs.append([module_name, contents_path])
                    else:
                        print("Unrecognized input: " + arg + " (should be module name, then a colon, then a directory or file ending in .vale, .vpst, .vast, .c)")
                        sys.exit(22)
                else:
                    user_modules.append(arg)

            vast_file = None
            if len(user_valestrom_inputs) > 0 and len(user_vast_files) == 0:
                proc = self.valestrom("build", user_modules, user_valestrom_inputs, valestrom_options)

                if proc.returncode == 0:
                    vast_file = self.build_dir / "build.vast"
                    pass
                elif proc.returncode == 22:
                    print(proc.stdout + "\n" + proc.stderr)
                    sys.exit(22)
                else:
                    print(f"Internal error while compiling {user_valestrom_inputs}:\n" + proc.stdout + "\n" + proc.stderr)
                    sys.exit(proc.returncode)
            elif len(user_vast_files) > 0 and len(user_valestrom_inputs) == 0:
                if len(user_vast_files) > 1:
                    print("Can't have more than one VAST file!")
                    sys.exit(1)
                vast_file = user_vast_files[0]
            else:
                print(f"Specify at least one .vale file, or exactly one .vast file (but not both)")
                sys.exit(1)


            proc = self.valec(str(vast_file), str(self.build_dir), midas_options)
            # print(proc.stdout)
            # print(proc.stderr)
            if proc.returncode != 0:
                print(f"valec couldn't compile {vast_file}:\n" + proc.stdout + "\n" + proc.stderr, file=sys.stderr)
                sys.exit(1)

            c_files = user_c_files.copy() + glob.glob(str(self.builtins_path / "*.c"))

            # Get .o or .obj
            o_files = glob.glob(str(vast_file.with_suffix(".o"))) + glob.glob(str(vast_file.with_suffix(".obj")))
            if len(o_files) == 0:
                print("Internal error, no produced object files!")
                sys.exit(1)
            if len(o_files) > 1:
                print("Internal error, multiple produced object files! " + ", ".join(o_files))
                sys.exit(1)


            clang_inputs = o_files + c_files
            proc = self.clang(
                [str(n) for n in clang_inputs],
                self.build_dir,
                self.build_dir / exe_file,
                exports_dir if add_exports_include_path else None)
            # print(proc.stdout)
            # print(proc.stderr)
            if proc.returncode != 0:
                print(f"Linker couldn't compile {clang_inputs}:\n" + proc.stdout + "\n" + proc.stderr, file=sys.stderr)
                sys.exit(1)

            print("Compiled to " + str(self.build_dir / exe_file))
        else:
            print("Unknown subcommand, specify `build`, `run`, etc. Use `help` for more.")
            sys.exit(1)

if __name__ == '__main__':
    ValeCompiler().compile_and_execute(sys.argv[1:])
