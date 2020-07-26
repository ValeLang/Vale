import unittest
import subprocess
import os.path
import os
import sys
import shutil
import glob
from subprocess import PIPE

from typing import Dict, Any, List, Callable


def procrun(args: List[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(args, stdout=PIPE, stderr=PIPE, text=True, **kwargs)


class ValeCompiler(unittest.TestCase):
    GENPATH: str = os.environ.get('GENPATH', "cmake-build-debug")

    def valestrom(self, vale_file: str,
                  vir_file: str) -> subprocess.CompletedProcess:
        driver = "test/Driver.jar"
        driver_class = "net.verdagon.vale.driver.Driver"
        return procrun(
            [
                "java",
                "-cp",
                driver,
                driver_class,
                "build",
                "-o",
                vir_file,
                vale_file
            ]
        )

    def valec(self, vir_file: str,
              o_files_dir: str) -> subprocess.CompletedProcess:
        assert self.GENPATH
        valec_path = shutil.which("valec")
        if not type(valec_path) is str:
            valec_path = "cmake-build-debug/valec"

        return procrun(
            [valec_path, "--verify", "--llvmir", "--output-dir",
             o_files_dir, vir_file])

    def clang(self, o_files: List[str],
              exe_file: str) -> subprocess.CompletedProcess:
        return procrun(["clang", "-o", exe_file] + o_files)

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
            self, vale_file: str) -> subprocess.CompletedProcess:
        build_dir = f"build"

        if os.path.exists(build_dir):
            shutil.rmtree(build_dir)
        os.makedirs(build_dir)

        vir_file = f"build.vir"
        proc = self.valestrom(f"{vale_file}", vir_file)
        # print(proc.stdout)
        # print(proc.stderr)
        self.assertEqual(proc.returncode, 0,
                         f"valestrom couldn't compile {vale_file}:\n" +
                         proc.stdout + "\n" + proc.stderr)

        proc = self.valec(vir_file, build_dir)
        self.assertEqual(proc.returncode, 0,
                         f"valec couldn't compile {vir_file}:\n" +
                         proc.stdout + "\n" + proc.stderr)

        exe_file = f"a.out"
        o_files = glob.glob(f"{build_dir}/*.o") + ["src/valestd/assert.c", "src/valestd/stdio.c", "src/valestd/str.c", "src/valestd/census.c", "src/valestd/weaks.c"]
        proc = self.clang(o_files, exe_file)
        self.assertEqual(proc.returncode, 0,
                         f"clang couldn't compile {o_files}:\n" +
                         proc.stdout + "\n" + proc.stderr)

        print("Compiled to " + exe_file)

if __name__ == '__main__':
    ValeCompiler().compile_and_execute(sys.argv[1])
