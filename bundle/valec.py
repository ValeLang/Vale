import unittest
import subprocess
import os.path
import os
import sys
import shutil
import glob
import argparse

from subprocess import PIPE

from typing import Dict, Any, List, Callable


def procrun(args: List[str], **kwargs) -> subprocess.CompletedProcess:
    print("Running: " + str(args))
    return subprocess.run(args, stdout=PIPE, stderr=PIPE, text=True, **kwargs)


class ValeCompiler(unittest.TestCase):
    GENPATH: str = os.environ.get('GENPATH', "cmake-build-debug")

    def valestrom(self, vale_files: List[str],
                  valestrom_options: List[str]) -> subprocess.CompletedProcess:
        driver = os.path.dirname(os.path.realpath(__file__)) + "/Driver.jar"
        # print(driver)
        driver_class = "net.verdagon.vale.driver.Driver"
        return procrun(
            [
                "java",
                "-cp",
                driver,
                driver_class,
                "build"
            ] + valestrom_options + vale_files
        )

    def valec(self, vir_file: str,
              o_files_dir: str,
              midas_options: List[str]) -> subprocess.CompletedProcess:
        assert self.GENPATH
        valec_path = shutil.which("valec")
        if not type(valec_path) is str:
            valec_path = os.path.dirname(os.path.realpath(__file__)) + "/valec"

        return procrun(
            [valec_path, "--verify", "--llvmir", "--output-dir",
             o_files_dir, vir_file] + midas_options)

    def clang(self, o_files: List[str],
              exe_file: str) -> subprocess.CompletedProcess:
        return procrun(["clang", "-O3", "-o", exe_file] + o_files)

    def exec(self, exe_file: str) -> subprocess.CompletedProcess:
        return procrun([f"./{exe_file}"])

    @classmethod
    def setUpClass(cls) -> None:
        print(
            f"Using valec from {cls.GENPATH}. " +
            "Set GENPATH env var if this is incorrect",
            file=sys.stderr
        )

    def setUp(self) -> None:
        self.GENPATH: str = type(self).GENPATH

    def compile_and_execute(
            self, args: str) -> subprocess.CompletedProcess:

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
        if "--region-override" in args:
            ind = args.index("--region-override")
            del args[ind]
            val = args[ind]
            del args[ind]
            midas_options.append("--region-override")
            midas_options.append(val)
        vale_files = args

        build_dir = f"build"

        if os.path.exists(build_dir):
            shutil.rmtree(build_dir)
        os.makedirs(build_dir)

        vir_file = f"build.vir"
        proc = self.valestrom(vale_files, ["-o", vir_file])
        # print(proc.stdout)
        # print(proc.stderr)
        if proc.returncode == 0:
          pass
        elif proc.returncode == 22:
          print(proc.stdout + "\n" + proc.stderr)
          sys.exit(22)
        else:
          print(f"Internal error while compiling {vale_files}:\n" + proc.stdout + "\n" + proc.stderr)
          sys.exit(proc.returncode)

        proc = self.valec(vir_file, build_dir, midas_options)
        # print(proc.stdout)
        # print(proc.stderr)
        self.assertEqual(proc.returncode, 0,
                         f"valec couldn't compile {vir_file}:\n" +
                         proc.stdout + "\n" + proc.stderr)

        exe_file = f"a.out"
        o_files = glob.glob(f"{build_dir}/*.o") + [
              os.path.dirname(os.path.realpath(__file__)) + "/stdlib/assert.c",
              os.path.dirname(os.path.realpath(__file__)) + "/stdlib/stdio.c",
              os.path.dirname(os.path.realpath(__file__)) + "/stdlib/str.c",
              os.path.dirname(os.path.realpath(__file__)) + "/stdlib/census.c",
              os.path.dirname(os.path.realpath(__file__)) + "/stdlib/weaks.c",
              os.path.dirname(os.path.realpath(__file__)) + "/stdlib/genHeap.c"
            ]
        proc = self.clang(o_files, exe_file)
        # print(proc.stdout)
        # print(proc.stderr)
        self.assertEqual(proc.returncode, 0,
                         f"clang couldn't compile {o_files}:\n" +
                         proc.stdout + "\n" + proc.stderr)

        print("Compiled to " + exe_file)

if __name__ == '__main__':
    ValeCompiler().compile_and_execute(sys.argv[1:])
