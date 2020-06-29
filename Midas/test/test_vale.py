import unittest
import subprocess
import os.path
import os
import sys
import shutil
import glob

from typing import Dict, Any, List, Callable


def procrun(args: List[str], **kwargs) -> subprocess.CompletedProcess:
    return subprocess.run(args, capture_output=True, text=True, **kwargs)


class ValeTest(unittest.TestCase):
    GENPATH: str = os.environ.get('GENPATH', "cmake-build-debug")

    def valestrom(self, vale_file: str,
                  vir_file: str) -> subprocess.CompletedProcess:
        driver = "Driver20200628.jar"
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
            ],
            check=True
        )

    def valec(self, vir_file: str,
              o_files_dir: str) -> subprocess.CompletedProcess:
        assert self.GENPATH
        return procrun(
            [f"../{self.GENPATH}/valec", "--verify", "--llvmir", "--output-dir",
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
        build_dir = f"test_build/{os.path.splitext(vale_file)[0]}_build"

        if os.path.exists(build_dir):
            shutil.rmtree(build_dir)
        os.makedirs(build_dir)

        vir_file = f"{build_dir}/{os.path.splitext(vale_file)[0]}.vir"
        proc = self.valestrom(vale_file, vir_file)
        self.assertEqual(proc.returncode, 0,
                         f"valestrom couldn't compile {vale_file}:\n" +
                         proc.stdout + "\n" + proc.stderr)

        proc = self.valec(vir_file, build_dir)
        self.assertEqual(proc.returncode, 0,
                         f"valec couldn't compile {vir_file}:\n" +
                         proc.stdout + "\n" + proc.stderr)

        exe_file = f"{build_dir}/{os.path.splitext(vale_file)[0]}"
        o_files = glob.glob(f"{build_dir}/*.o")
        proc = self.clang(o_files, exe_file)
        self.assertEqual(proc.returncode, 0,
                         f"clang couldn't compile {o_files}:\n" +
                         proc.stdout + "\n" + proc.stderr)

        proc = self.exec(exe_file)
        return proc

    def compile_and_execute_and_expect_return_code(self, vale_file: str,
                                                   expected_return_code) -> None:
        proc = self.compile_and_execute(vale_file)
        self.assertEqual(proc.returncode, expected_return_code,
                         f"Unexpected result: {proc.returncode}")

    def test_addret(self) -> None:
        self.compile_and_execute_and_expect_return_code("addret.vale", 7)

    def test_if(self) -> None:
        self.compile_and_execute_and_expect_return_code("if.vale", 42)

    def test_mutlocal(self) -> None:
        self.compile_and_execute_and_expect_return_code("mutlocal.vale", 42)

    def test_while(self) -> None:
        self.compile_and_execute_and_expect_return_code("while.vale", 42)

    def test_immstruct(self) -> None:
        self.compile_and_execute_and_expect_return_code("immstruct.vale", 5)


if __name__ == '__main__':
    unittest.main()
