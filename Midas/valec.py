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
                self.valestrom_path,
                "net.verdagon.vale.driver.Driver",
                "build"
            ] + valestrom_options + vale_files
        )

    def valec(self,
              vir_file: str,
              o_files_dir: str,
              midas_options: List[str]) -> subprocess.CompletedProcess:
        return procrun(
            [self.valec_path, "--verify", "--output-dir",
             o_files_dir, vir_file] + midas_options)

    def clang(self,
              o_files: List[str],
              exe_file: str) -> subprocess.CompletedProcess:
        if self.windows:
            return procrun(["cl.exe", '/ENTRY:"main"', '/SUBSYSTEM:CONSOLE', "/Fe:" + exe_file] + o_files)
        else:
            return procrun(["clang-7", "-O3", "-lm", "-o", exe_file] + o_files)

    def exec(self, exe_file: str) -> subprocess.CompletedProcess:
        return procrun([f"./{exe_file}"])

    def compile_and_execute(
            self, args: str) -> subprocess.CompletedProcess:


        cwd = os.path.dirname(os.path.realpath(__file__))



        self.valestrom_path = os.environ.get('VALESTROM_PATH', '')
        if len(self.valestrom_path) > 0:
            pass
        elif path.exists(cwd + "/Driver.jar"):
            self.valestrom_path = cwd + "/Driver.jar"
        elif path.exists(cwd + "/test/Driver.jar"):
            self.valestrom_path = cwd + "/test/Driver.jar"

        self.valestd_path = os.environ.get('VALESTD_PATH', '')
        if len(self.valestd_path) > 0:
            pass
        elif path.exists(cwd + "/src/valestd"):
            self.valestd_path = cwd + "/src/valestd"
        elif path.exists(cwd + "/runtime"):
            self.valestd_path = cwd + "/runtime"

        # Maybe we can add a command line param here too, relying on environments is always irksome.
        self.valec_path: str = os.environ.get('VALEC_PATH', '')
        if len(self.valec_path) > 0:
            print(f"Using valec at {valec_path}. ", file=sys.stderr)
        elif shutil.which("valec") != None:
            self.valec_path = shutil.which("valec")
            print(f"No VALEC_PATH in env, assuming the one in {self.valec_path}", file=sys.stderr)
        elif path.exists(cwd + "/valec"):
            self.valec_path = cwd + "/valec"
            print("No VALEC_PATH in env, assuming the one in current directory.", file=sys.stderr)
        elif path.exists(cwd + "/Midas.exe"):
            self.valec_path = cwd + "/Midas.exe"
            print("No VALEC_PATH in env, assuming the one in current directory.", file=sys.stderr)
        elif path.exists(cwd + "/cmake-build-debug/valec"):
            self.valec_path = cwd + "/cmake-build-debug/valec"
            print("No VALEC_PATH in env, assuming the one in cmake-build-debug.", file=sys.stderr)
        elif path.exists(cwd + "/x64/Debug/Midas.exe"):
            self.valec_path = cwd + "/x64/Debug/Midas.exe"
            print("No VALEC_PATH in env, assuming the one in x64/Debug.", file=sys.stderr)
        elif path.exists(cwd + "/x64/Release/Midas.exe"):
            self.valec_path = cwd + "/x64/Release/Midas.exe"
            print("No VALEC_PATH in env, assuming the one in x64/Release.", file=sys.stderr)
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

        build_dir = f"."
        exe_file = ("main.exe" if self.windows else "a.out")

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
        if "--verify" in args:
            args.remove("--verify")
            midas_options.append("--verify")
        if "--verbose" in args:
            args.remove("--verbose")
            valestrom_options.append("--verbose")
        if "--llvmir" in args:
            args.remove("--llvmir")
            midas_options.append("--llvmir")
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
            build_dir = val
            midas_options.append("--output-dir")
            midas_options.append(val)
        if "-o" in args:
            ind = args.index("-o")
            del args[ind]
            val = args[ind]
            del args[ind]
            exe_file = val

        vale_files = []
        c_files = []
        for arg in args:
            if arg.endswith(".vale"):
                vale_files.append(arg)
            elif arg.endswith(".c"):
                c_files.append(arg)
            else:
                print("Unrecognized input: " + arg)
                sys.exit(22)

        if build_dir != ".":
            if os.path.exists(build_dir):
                shutil.rmtree(build_dir)
            os.makedirs(build_dir)

        vir_file = build_dir + "/build.vir"
        valestrom_options.append("-o")
        valestrom_options.append(vir_file)
        proc = self.valestrom(vale_files, valestrom_options)
        print(proc.stdout)
        print(proc.stderr)
        if proc.returncode == 0:
          pass
        elif proc.returncode == 22:
          print(proc.stdout + "\n" + proc.stderr)
          sys.exit(22)
        else:
          print(f"Internal error while compiling {vale_files}:\n" + proc.stdout + "\n" + proc.stderr)
          sys.exit(proc.returncode)

        proc = self.valec(vir_file, build_dir, midas_options)
        print(proc.stdout)
        print(proc.stderr)
        if proc.returncode != 0:
             print(f"valec couldn't compile {vir_file}:\n" + proc.stdout + "\n" + proc.stderr, file=sys.stderr)
             sys.exit(1)


        o_files = (
            glob.glob(f"{build_dir}/*.o") + 
            glob.glob(f"{build_dir}/*.obj") +
            glob.glob(f"{self.valestd_path}/*.c") +
            c_files)
        proc = self.clang(o_files, build_dir + "/" + exe_file)
        # print(proc.stdout)
        # print(proc.stderr)
        if proc.returncode != 0:
             print(f"Linker couldn't compile {o_files}:\n" + proc.stdout + "\n" + proc.stderr, file=sys.stderr)
             sys.exit(1)

        print("Compiled to " + build_dir + "/" + exe_file)

if __name__ == '__main__':
    ValeCompiler().compile_and_execute(sys.argv[1:])
