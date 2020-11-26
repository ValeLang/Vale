import unittest
import subprocess
import os.path
import os
import sys
import shutil
import glob
import argparse
import platform
import os.path

from pathlib import PurePath
from os import path
from subprocess import PIPE
from typing import Dict, Any, List, Callable


def procrun(args: List[str], **kwargs) -> subprocess.CompletedProcess:
    print("Running: " + " ".join(args))
    return subprocess.run(args, stdout=PIPE, stderr=PIPE, text=True, **kwargs)


class ValeCompiler:
    def valestrom(self,
                  vale_files: List[str],
                  valestrom_options: List[str]) -> subprocess.CompletedProcess:
        return procrun(
            [
                "java",
                "-cp",
                str(self.valestrom_path / "Valestrom.jar"), # + ":" \
                #+ str((self.valestrom_path / "lift-json_2.12-3.3.0-RC1.jar")),
                "net.verdagon.vale.driver.Driver",
                "build"
            ] + valestrom_options + vale_files
        )

    def valec(self,
              vir_file: str,
              o_files_dir: str,
              midas_options: List[str]) -> subprocess.CompletedProcess:
        return procrun(
            [str(self.valec_path), "--verify", "--output-dir", o_files_dir, vir_file] + midas_options)

    def clang(self,
              o_files: List[str],
              exe_file: str) -> subprocess.CompletedProcess:
        if self.windows:
            return procrun(["link.exe", '/ENTRY:"main"', '/SUBSYSTEM:CONSOLE', "/Fe:" + exe_file] + o_files)
        else:
            return procrun(["clang-7", "-O3", "-lm", "-o", exe_file] + o_files)

    def exec(self, exe_file: str) -> subprocess.CompletedProcess:
        return procrun([f"./{exe_file}"])

    def compile_and_execute(
        self, args: str) -> subprocess.CompletedProcess:


        cwd = PurePath(os.path.dirname(os.path.realpath(__file__)))




        if len(os.environ.get('VALESTROM_PATH', '')) > 0:
            self.valestrom_path = PurePath(os.environ.get('VALESTROM_PATH', ''))
        elif path.exists(cwd / "Valestrom.jar"):
            self.valestrom_path = cwd
        elif path.exists(cwd / "test/Valestrom.jar"):
            self.valestrom_path = cwd / "test"
        elif path.exists(cwd / "../Valestrom/out/artifacts/Valestrom_jar/Valestrom.jar"):
            self.valestrom_path = cwd / "../Valestrom/out/artifacts/Valestrom_jar"
        else:
            self.valestrom_path = cwd

        if len(os.environ.get('VALESTD_PATH', '')) > 0:
            self.valestd_path = PurePath(os.environ.get('VALESTD_PATH', ''))
        elif path.exists(cwd / "src/valestd"):
            self.valestd_path = cwd / "src/valestd"
        elif path.exists(cwd / "runtime"):
            self.valestd_path = cwd / "runtime"
        else:
            self.valestd_path = cwd

        # Maybe we can add a command line param here too, relying on environments is always irksome.
        self.valec_path: PurePath = cwd
        if len(os.environ.get('VALEC_PATH', '')) > 0:
            print(f"Using valec at {self.valec_path}. ", file=sys.stderr)
            self.valec_path = PurePath(os.environ.get('VALEC_PATH', ''))
        elif shutil.which("valec") != None:
            self.valec_path = PurePath(shutil.which("valec"))
            print(f"No VALEC_PATH in env, assuming the one in {self.valec_path}", file=sys.stderr)
        elif path.exists(cwd / "valec"):
            self.valec_path = cwd / "valec"
            print("No VALEC_PATH in env, assuming the one in current directory.", file=sys.stderr)
        elif path.exists(cwd / "Midas.exe"):
            self.valec_path = cwd / "Midas.exe"
            print("No VALEC_PATH in env, assuming the one in current directory.", file=sys.stderr)
        elif path.exists(cwd / "cmake-build-debug/valec"):
            self.valec_path = cwd / "cmake-build-debug/valec"
            print("No VALEC_PATH in env, assuming the one in cmake-build-debug.", file=sys.stderr)
        elif path.exists(cwd / "x64/Debug/Midas.exe"):
            self.valec_path = cwd / "x64/Debug/Midas.exe"
            print("No VALEC_PATH in env, assuming the one in x64/Debug.", file=sys.stderr)
        elif path.exists(cwd / "x64/Release/Midas.exe"):
            self.valec_path = cwd / "x64/Release/Midas.exe"
            print("No VALEC_PATH in env, assuming the one in x64/Release.", file=sys.stderr)
        else:
            print("No VALEC_PATH in env, and couldn't find one nearby, aborting!", file=sys.stderr)
            sys.exit(1)
        print("valec path: " + str(self.valec_path))

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

        build_dir = PurePath(f".")
        exe_file = ("main.exe" if self.windows else "a.out")
        parseds_output_dir = None

        valestrom_options = []
        midas_options = []
        if "--flares" in args:
            args.remove("--flares")
            midas_options.append("--flares")
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
        if "--llvmir" in args:
            args.remove("--llvmir")
            midas_options.append("--llvmir")
        if "--elide-checks-for-known-live" in args:
            args.remove("--elide-checks-for-known-live")
            midas_options.append("--elide-checks-for-known-live")
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
            build_dir = PurePath(val)
            midas_options.append("--output-dir")
            midas_options.append(val)
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

        user_valestrom_files = []
        user_vir_files = []
        user_c_files = []

        for arg in args:
            if arg.endswith(".vale"):
                user_valestrom_files.append(arg)
            elif arg.endswith(".vpr"):
                user_valestrom_files.append(arg)
            elif arg.endswith(".vir"):
                user_vir_files.append(arg)
            elif arg.endswith(".c"):
                user_c_files.append(arg)
            else:
                print("Unrecognized input: " + arg)
                sys.exit(22)

        vir_file = None
        if len(user_valestrom_files) > 0 and len(user_vir_files) == 0:
            # Add in the default vale files
            user_valestrom_files = (
                user_valestrom_files +
                glob.glob(str(cwd / "vstl/*utils.vale")) +
                [str(cwd / "vstl/strings.vale"), str(cwd / "vstl/opt.vale")])

            if build_dir != PurePath("."):
                if os.path.exists(build_dir):
                    shutil.rmtree(build_dir)
                os.makedirs(build_dir)

            output_vir_file = build_dir / "build.vir"
            valestrom_options.append("-o")
            valestrom_options.append(str(output_vir_file))

            if parseds_output_dir != None:
                valestrom_options.append("-op")
                valestrom_options.append(str(parseds_output_dir))

            proc = self.valestrom(user_valestrom_files, valestrom_options)
            # print(proc.stdout)
            # print(proc.stderr)
            if proc.returncode == 0:
                vir_file = output_vir_file
                pass
            elif proc.returncode == 22:
                print(proc.stdout + "\n" + proc.stderr)
                sys.exit(22)
            else:
                print(f"Internal error while compiling {user_valestrom_files}:\n" + proc.stdout + "\n" + proc.stderr)
                sys.exit(proc.returncode)
        elif len(user_vir_files) > 0 and len(user_valestrom_files) == 0:
            if len(user_vir_files) > 1:
                print("Can't have more than one VIR file!")
                sys.exit(1)
            vir_file = user_vir_files[0]
        else:
            print(f"Specify at least one .vale file, or exactly one .vir file (but not both)")
            sys.exit(1)


        proc = self.valec(str(vir_file), str(build_dir), midas_options)
        # print(proc.stdout)
        # print(proc.stderr)
        if proc.returncode != 0:
            print(f"valec couldn't compile {vir_file}:\n" + proc.stdout + "\n" + proc.stderr, file=sys.stderr)
            sys.exit(1)

        c_files = user_c_files.copy() + glob.glob(str(self.valestd_path / "*.c")) + [str(cwd / "vstl/strings.c")]

        # Get .o or .obj
        o_files = glob.glob(str(vir_file.with_suffix(".o"))) + glob.glob(str(vir_file.with_suffix(".obj")))
        if len(o_files) == 0:
            print("Internal error, no produced object files!")
            sys.exit(1)
        if len(o_files) > 1:
            print("Internal error, multiple produced object files! " + ", ".join(o_files))
            sys.exit(1)


        clang_inputs = o_files + c_files
        proc = self.clang([str(n) for n in clang_inputs], str(build_dir / exe_file))
        # print(proc.stdout)
        # print(proc.stderr)
        if proc.returncode != 0:
            print(f"Linker couldn't compile {clang_inputs}:\n" + proc.stdout + "\n" + proc.stderr, file=sys.stderr)
            sys.exit(1)

        print("Compiled to " + str(build_dir / exe_file))

if __name__ == '__main__':
    ValeCompiler().compile_and_execute(sys.argv[1:])
