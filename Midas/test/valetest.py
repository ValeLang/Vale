import unittest
import subprocess
import platform
import os.path
import os
import sys
import shutil
import glob

from typing import Dict, Any, List, Callable


def procrun(args: List[str], **kwargs) -> subprocess.CompletedProcess:
    # print("Running: " + " ".join(args))
    return subprocess.run(args, capture_output=True, text=True, **kwargs)


PATH_TO_SAMPLES = "../Valestrom/Tests/test/main/resources/"

class ValeTest(unittest.TestCase):
    GENPATH: str = os.environ.get('GENPATH', ".")

    def valec(self,
              module_name: str,
              in_filepaths: List[str],
              o_files_dir: str,
              exe_name: str,
              region_override: str,
              extra_flags: List[str]) -> subprocess.CompletedProcess:
        assert self.GENPATH
        python = "python" if self.windows else "python3"
        return procrun(
            [python,
             f"{self.GENPATH}/valec.py",
             "build",
             module_name,
             "--verify",
             "--llvmir",
             "--census",
             "--flares",
             "--region-override", region_override,
             "--output-dir", o_files_dir,
             "--add-exports-include-path",
             "-o",
             exe_name] + extra_flags + in_filepaths)

    def exec(self, exe_file: str) -> subprocess.CompletedProcess:
        return procrun([f"./{exe_file}"])

    @classmethod
    def setUpClass(cls) -> None:
        print(
            f"Using valec from {cls.GENPATH}. " +
            "Set GENPATH env var if this is incorrect",
            file=sys.stderr)

    def setUp(self) -> None:
        self.GENPATH: str = type(self).GENPATH
        self.windows = platform.system() == 'Windows'

    def compile_and_execute(
            self,
            in_filepaths: List[str],
            region_override: str,
            extra_flags: List[str]) -> subprocess.CompletedProcess:
        first_vale_filepath = in_filepaths[0]
        file_name_without_extension = os.path.splitext(os.path.basename(first_vale_filepath))[0]
        build_dir = f"test/test_build/{file_name_without_extension}_build"

        module_name = "tmod"
        for i in range(0, len(in_filepaths)):
            in_filepaths[i] = module_name + ":" + in_filepaths[i]

        proc = self.valec(module_name, in_filepaths, build_dir, file_name_without_extension, region_override, extra_flags)
        self.assertEqual(proc.returncode, 0,
                         f"valec couldn't compile {in_filepaths}:\n" +
                         proc.stdout + "\n" + proc.stderr)

        exe_file = f"{build_dir}/{file_name_without_extension}"

        proc = self.exec(exe_file)
        return proc

    def compile_and_execute_and_expect_return_code(
            self,
            vale_files: List[str],
            region_override: str,
            expected_return_code: int,
            extra_flags: List[str] = None) -> None:
        if extra_flags is None:
            extra_flags = []
        proc = self.compile_and_execute(vale_files, region_override, extra_flags)
        # print(proc.stdout)
        # print(proc.stderr)
        if proc.returncode != expected_return_code:
            first_vale_filepath = vale_files[0]
            file_name_without_extension = os.path.splitext(os.path.basename(first_vale_filepath))[0]
            build_dir = f"test/test_build/{file_name_without_extension}_build"
            textfile = open(build_dir + "/stdout.txt", "w")
            a = textfile.write(proc.stdout)
            textfile.close()
            textfile = open(build_dir + "/stderr.txt", "w")
            a = textfile.write(proc.stderr)
            textfile.close()
            self.assertEqual(proc.returncode, expected_return_code,
                             f"Unexpected result: {proc.returncode}\n" + proc.stdout + proc.stderr)

    # Tests for immutables in exports/externs
    def test_assist_strlenextern(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/strlenextern"], "assist", 11)
    def test_assist_voidreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/voidreturnexport"], "assist", 42)
    def test_assist_structimmparamextern(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structimmparamextern"], "assist", 42)
    def test_assist_structimmparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structimmparamexport"], "assist", 42)
    def test_assist_structimmparamdeepextern(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structimmparamdeepextern"], "assist", 42)
    def test_assist_structimmparamdeepexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structimmparamdeepexport"], "assist", 42)
    def test_assist_interfaceimmparamextern(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfaceimmparamextern"], "assist", 42)
    def test_assist_interfaceimmparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfaceimmparamexport"], "assist", 42)
    def test_assist_interfaceimmparamdeepextern(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfaceimmparamdeepextern"], "assist", 42)
    def test_assist_interfaceimmparamdeepexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfaceimmparamdeepexport"], "assist", 42)
    def test_assist_rsaimmreturnextern(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsaimmreturnextern"], "assist", 42)
    def test_assist_rsaimmreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsaimmreturnexport"], "assist", 42)
    def test_assist_rsaimmparamextern(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsaimmparamextern"], "assist", 10)
    def test_assist_rsaimmparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsaimmparamexport"], "assist", 10)
    def test_assist_rsaimmparamdeepextern(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsaimmparamdeepextern"], "assist", 20)
    def test_assist_rsaimmparamdeepexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsaimmparamdeepexport"], "assist", 42)
    def test_assist_ssaimmparamextern(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssaimmparamextern"], "assist", 42)
    def test_assist_ssaimmparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssaimmparamexport"], "assist", 42)
    def test_assist_ssaimmreturnextern(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssaimmreturnextern"], "assist", 42)
    def test_assist_ssaimmreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssaimmreturnexport"], "assist", 42)
    def test_assist_ssaimmparamdeepextern(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssaimmparamdeepextern"], "assist", 42)
    def test_assist_ssaimmparamdeepexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssaimmparamdeepexport"], "assist", 42)

    def test_assist_voidreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/voidreturnexport"], "assist", 42)
    def test_assist_strreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/strreturnexport"], "assist", 6)
    def test_assist_structimmparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structimmparamexport"], "assist", 42)
    def test_assist_structimmparamdeepexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structimmparamdeepexport"], "assist", 42)
    def test_assist_interfaceimmparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfaceimmparamexport"], "assist", 42)
    def test_assist_rsaimmparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsaimmparamexport"], "assist", 10)
    def test_assist_smallstr(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/smallstr.vale"], "assist", 42)
    def test_assist_immtupleaccess(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/tuples/immtupleaccess.vale"], "assist", 42)
    def test_assist_ssaimmfromcallable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/ssaimmfromcallable.vale"], "assist", 42)
    def test_assist_ssaimmfromvalues(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/ssaimmfromvalues.vale"], "assist", 42)

    # kldc = known live double check
    def test_resilientv3_kldc(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/deadmutstruct.vale"], "resilient-v3", 116, ["--override-known-live-true"])

    def test_twinpages(self) -> None:
        proc = procrun(["clang", "test/testtwinpages.c", "-o", "test/test_build/testtwinpages"])
        self.assertEqual(proc.returncode, 0, f"Twin pages test failed!")

    def test_assist_addret(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/addret.vale"], "assist", 7)
    def test_assist_add64ret(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/add64ret.vale"], "assist", 42)
    def test_assist_floatarithmetic(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/floatarithmetic.vale"], "assist", 42)
    def test_assist_floateq(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/floateq.vale"], "assist", 42)
    def test_assist_concatstrfloat(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/concatstrfloat.vale"], "assist", 42)

    # def test_resilientv4_tether(self) -> None:
    #     self.compile_and_execute_and_expect_return_code(["test/tether.vale"], "resilient-v4", 0)

    def test_assist_mutswaplocals(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutswaplocals.vale"], "assist", 42)
    def test_unsafefast_mutswaplocals(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutswaplocals.vale"], "unsafe-fast", 42)
    # def test_resilientv4_mutswaplocals(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutswaplocals.vale"], "resilient-v4", 42)
    def test_resilientv3_mutswaplocals(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutswaplocals.vale"], "resilient-v3", 42)
    def test_naiverc_mutswaplocals(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutswaplocals.vale"], "naive-rc", 42)

    def test_assist_rsamutreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsamutreturnexport"], "assist", 42)
    def test_unsafefast_rsamutreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsamutreturnexport"], "unsafe-fast", 42)
    # def test_resilientv4_rsamutreturnexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsamutreturnexport"], "resilient-v4", 42)
    def test_resilientv3_rsamutreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsamutreturnexport"], "resilient-v3", 42)
    # def test_naiverc_rsamutreturnexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsamutreturnexport"], "naive-rc", 42)

    def test_assist_ssamutreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssamutreturnexport"], "assist", 42)
    def test_unsafefast_ssamutreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssamutreturnexport"], "unsafe-fast", 42)
    # def test_resilientv4_ssamutreturnexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssamutreturnexport"], "resilient-v4", 42)
    def test_resilientv3_ssamutreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssamutreturnexport"], "resilient-v3", 42)
    # def test_naiverc_ssamutreturnexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssamutreturnexport"], "naive-rc", 42)

    def test_assist_structimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structimm.vale"], "assist", 5)
    def test_unsafefast_structimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structimm.vale"], "unsafe-fast", 5)
    # def test_resilientv4_structimm(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structimm.vale"], "resilient-v4", 5)
    def test_resilientv3_structimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structimm.vale"], "resilient-v3", 5)
    def test_naiverc_structimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structimm.vale"], "naive-rc", 5)

    def test_assist_memberrefcount(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/memberrefcount.vale"], "assist", 5)
    def test_unsafefast_memberrefcount(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/memberrefcount.vale"], "unsafe-fast", 5)
    # def test_resilientv4_memberrefcount(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/memberrefcount.vale"], "resilient-v4", 5)
    def test_resilientv3_memberrefcount(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/memberrefcount.vale"], "resilient-v3", 5)
    def test_naiverc_memberrefcount(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/memberrefcount.vale"], "naive-rc", 5)

    def test_assist_bigstructimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/bigstructimm.vale"], "assist", 42)
    def test_unsafefast_bigstructimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/bigstructimm.vale"], "unsafe-fast", 42)
    # def test_resilientv4_bigstructimm(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/bigstructimm.vale"], "resilient-v4", 42)
    def test_resilientv3_bigstructimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/bigstructimm.vale"], "resilient-v3", 42)
    def test_naiverc_bigstructimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/bigstructimm.vale"], "naive-rc", 42)

    def test_assist_structmut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structmut.vale"], "assist", 8)
    def test_unsafefast_structmut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structmut.vale"], "unsafe-fast", 8)
    # def test_resilientv4_structmut(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structmut.vale"], "resilient-v4", 8)
    def test_resilientv3_structmut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structmut.vale"], "resilient-v3", 8)
    def test_naiverc_structmut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structmut.vale"], "naive-rc", 8)

    def test_assist_lambda(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambda.vale"], "assist", 42)
    def test_unsafefast_lambda(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambda.vale"], "unsafe-fast", 42)
    # def test_resilientv4_lambda(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambda.vale"], "resilient-v4", 42)
    def test_resilientv3_lambda(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambda.vale"], "resilient-v3", 42)
    def test_naiverc_lambda(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambda.vale"], "naive-rc", 42)

    def test_assist_if(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/if.vale"], "assist", 42)
    def test_unsafefast_if(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/if.vale"], "unsafe-fast", 42)
    # def test_resilientv4_if(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/if.vale"], "resilient-v4", 42)
    def test_resilientv3_if(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/if.vale"], "resilient-v3", 42)
    def test_naiverc_if(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/if.vale"], "naive-rc", 42)

    def test_assist_upcastif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/upcastif.vale"], "assist", 42)
    def test_unsafefast_upcastif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/upcastif.vale"], "unsafe-fast", 42)
    # def test_resilientv4_upcastif(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/upcastif.vale"], "resilient-v4", 42)
    def test_resilientv3_upcastif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/upcastif.vale"], "resilient-v3", 42)
    def test_naiverc_upcastif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/upcastif.vale"], "naive-rc", 42)

    def test_assist_ifnevers(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/ifnevers.vale"], "assist", 42)
    def test_unsafefast_ifnevers(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/ifnevers.vale"], "unsafe-fast", 42)
    # def test_resilientv4_ifnevers(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/ifnevers.vale"], "resilient-v4", 42)
    def test_resilientv3_ifnevers(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/ifnevers.vale"], "resilient-v3", 42)
    def test_naiverc_ifnevers(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/ifnevers.vale"], "naive-rc", 42)

    def test_assist_mutlocal(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutlocal.vale"], "assist", 42)
    def test_unsafefast_mutlocal(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutlocal.vale"], "unsafe-fast", 42)
    # def test_resilientv4_mutlocal(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutlocal.vale"], "resilient-v4", 42)
    def test_resilientv3_mutlocal(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutlocal.vale"], "resilient-v3", 42)
    def test_naiverc_mutlocal(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/mutlocal.vale"], "naive-rc", 42)

    def test_assist_while(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/while/while.vale"], "assist", 42)
    def test_unsafefast_while(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/while/while.vale"], "unsafe-fast", 42)
    # def test_resilientv4_while(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/while/while.vale"], "resilient-v4", 42)
    def test_resilientv3_while(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/while/while.vale"], "resilient-v3", 42)
    def test_naiverc_while(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/while/while.vale"], "naive-rc", 42)

    def test_assist_constraintRef(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/constraintRef.vale"], "assist", 8)
    def test_unsafefast_constraintRef(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/constraintRef.vale"], "unsafe-fast", 8)
    # def test_resilientv4_constraintRef(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/constraintRef.vale"], "resilient-v4", 8)
    def test_resilientv3_constraintRef(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/constraintRef.vale"], "resilient-v3", 8)
    def test_naiverc_constraintRef(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/constraintRef.vale"], "naive-rc", 8)

    def test_assist_ssamutfromcallable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/ssamutfromcallable.vale"], "assist", 42)
    def test_unsafefast_ssamutfromcallable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/ssamutfromcallable.vale"], "unsafe-fast", 42)
    # def test_resilientv4_ssamutfromcallable(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/ssamutfromcallable.vale"], "resilient-v4", 42)
    def test_resilientv3_ssamutfromcallable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/ssamutfromcallable.vale"], "resilient-v3", 42)
    def test_naiverc_ssamutfromcallable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/ssamutfromcallable.vale"], "naive-rc", 42)

    def test_assist_ssamutfromvalues(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/ssamutfromvalues.vale"], "assist", 42)
    def test_unsafefast_ssamutfromvalues(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/ssamutfromvalues.vale"], "unsafe-fast", 42)
    # def test_resilientv4_ssamutfromvalues(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/ssamutfromvalues.vale"], "resilient-v4", 42)
    def test_resilientv3_ssamutfromvalues(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/ssamutfromvalues.vale"], "resilient-v3", 42)
    def test_naiverc_ssamutfromvalues(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/ssamutfromvalues.vale"], "naive-rc", 42)

    def test_assist_interfaceimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/interfaceimm.vale"], "assist", 42)
    def test_unsafefast_interfaceimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/interfaceimm.vale"], "unsafe-fast", 42)
    # def test_resilientv4_interfaceimm(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/interfaceimm.vale"], "resilient-v4", 42)
    def test_resilientv3_interfaceimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/interfaceimm.vale"], "resilient-v3", 42)
    def test_naiverc_interfaceimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/interfaceimm.vale"], "naive-rc", 42)

    def test_assist_interfacemut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/interfacemut.vale"], "assist", 42)
    def test_unsafefast_interfacemut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/interfacemut.vale"], "unsafe-fast", 42)
    # def test_resilientv4_interfacemut(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/interfacemut.vale"], "resilient-v4", 42)
    def test_resilientv3_interfacemut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/interfacemut.vale"], "resilient-v3", 42)
    def test_naiverc_interfacemut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/virtuals/interfacemut.vale"], "naive-rc", 42)

    def test_assist_structmutstore(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structmutstore.vale"], "assist", 42)
    def test_unsafefast_structmutstore(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structmutstore.vale"], "unsafe-fast", 42)
    # def test_resilientv4_structmutstore(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structmutstore.vale"], "resilient-v4", 42)
    def test_resilientv3_structmutstore(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structmutstore.vale"], "resilient-v3", 42)
    def test_naiverc_structmutstore(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structmutstore.vale"], "naive-rc", 42)

    def test_assist_structmutstoreinner(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structmutstoreinner.vale"], "assist", 42)
    def test_unsafefast_structmutstoreinner(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structmutstoreinner.vale"], "unsafe-fast", 42)
    # def test_resilientv4_structmutstoreinner(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structmutstoreinner.vale"], "resilient-v4", 42)
    def test_resilientv3_structmutstoreinner(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structmutstoreinner.vale"], "resilient-v3", 42)
    def test_naiverc_structmutstoreinner(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/structs/structmutstoreinner.vale"], "naive-rc", 42)

    def test_assist_rsaimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/rsaimm.vale"], "assist", 3)
    def test_unsafefast_rsaimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/rsaimm.vale"], "unsafe-fast", 3)
    # def test_resilientv4_rsaimm(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/rsaimm.vale"], "resilient-v4", 3)
    def test_resilientv3_rsaimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/rsaimm.vale"], "resilient-v3", 3)
    def test_naiverc_rsaimm(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/rsaimm.vale"], "naive-rc", 3)

    def test_assist_rsamut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/rsamut.vale"], "assist", 3)
    def test_unsafefast_rsamut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/rsamut.vale"], "unsafe-fast", 3)
    # def test_resilientv4_rsamut(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/rsamut.vale"], "resilient-v4", 3)
    def test_resilientv3_rsamut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/rsamut.vale"], "resilient-v3", 3)
    def test_naiverc_rsamut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/rsamut.vale"], "naive-rc", 3)

    def test_assist_interfacemutreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfacemutreturnexport"], "assist", 42)
    def test_unsafefast_interfacemutreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfacemutreturnexport"], "unsafe-fast", 42)
    # def test_resilientv4_interfacemutreturnexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfacemutreturnexport"], "resilient-v4", 42)
    def test_resilientv3_interfacemutreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfacemutreturnexport"], "resilient-v3", 42)
    # def test_naiverc_interfacemutreturnexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfacemutreturnexport"], "naive-rc", 42)

    def test_assist_interfacemutparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfacemutparamexport"], "assist", 42)
    def test_unsafefast_interfacemutparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfacemutparamexport"], "unsafe-fast", 42)
    # def test_resilientv4_interfacemutparamexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfacemutparamexport"], "resilient-v4", 42)
    def test_resilientv3_interfacemutparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfacemutparamexport"], "resilient-v3", 42)
    # def test_naiverc_interfacemutparamexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfacemutparamexport"], "naive-rc", 42)

    def test_assist_structmutreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structmutreturnexport"], "assist", 42)
    def test_unsafefast_structmutreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structmutreturnexport"], "unsafe-fast", 42)
    # def test_resilientv4_structmutreturnexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structmutreturnexport"], "resilient-v4", 42)
    def test_resilientv3_structmutreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structmutreturnexport"], "resilient-v3", 42)
    # def test_naiverc_structmutreturnexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structmutreturnexport"], "naive-rc", 42)

    def test_assist_structmutparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structmutparamexport"], "assist", 42)
    def test_unsafefast_structmutparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structmutparamexport"], "unsafe-fast", 42)
    # def test_resilientv4_structmutparamexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structmutparamexport"], "resilient-v4", 42)
    def test_resilientv3_structmutparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structmutparamexport"], "resilient-v3", 42)
    # def test_naiverc_structmutparamexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/structmutparamexport"], "naive-rc", 42)

    def test_assist_rsamutparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsamutparamexport"], "assist", 10)
    def test_unsafefast_rsamutparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsamutparamexport"], "unsafe-fast", 10)
    # def test_resilientv4_rsamutparamexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsamutparamexport"], "resilient-v4", 10)
    def test_resilientv3_rsamutparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsamutparamexport"], "resilient-v3", 10)
    # def test_naiverc_rsamutparamexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/rsamutparamexport"], "naive-rc", 10)

    def test_assist_ssamutparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssamutparamexport"], "assist", 10)
    def test_unsafefast_ssamutparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssamutparamexport"], "unsafe-fast", 10)
    # def test_resilientv4_ssamutparamexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssamutparamexport"], "resilient-v4", 10)
    def test_resilientv3_ssamutparamexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssamutparamexport"], "resilient-v3", 10)
    # def test_naiverc_ssamutparamexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/ssamutparamexport"], "naive-rc", 10)

    def test_assist_rsamutlen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/rsamutlen.vale"], "assist", 5)
    def test_unsafefast_rsamutlen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/rsamutlen.vale"], "unsafe-fast", 5)
    # def test_resilientv4_rsamutlen(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/rsamutlen.vale"], "resilient-v4", 5)
    def test_resilientv3_rsamutlen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/rsamutlen.vale"], "resilient-v3", 5)
    def test_naiverc_rsamutlen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/rsamutlen.vale"], "naive-rc", 5)

    def test_assist_stradd(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/stradd.vale"], "assist", 42)
    def test_unsafefast_stradd(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/stradd.vale"], "unsafe-fast", 42)
    # def test_resilientv4_stradd(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/stradd.vale"], "resilient-v4", 42)
    def test_resilientv3_stradd(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/stradd.vale"], "resilient-v3", 42)
    def test_naiverc_stradd(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/stradd.vale"], "naive-rc", 42)

    def test_assist_strneq(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strneq.vale"], "assist", 42)
    def test_unsafefast_strneq(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strneq.vale"], "unsafe-fast", 42)
    # def test_resilientv4_strneq(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strneq.vale"], "resilient-v4", 42)
    def test_resilientv3_strneq(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strneq.vale"], "resilient-v3", 42)
    def test_naiverc_strneq(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strneq.vale"], "naive-rc", 42)

    def test_assist_lambdamut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambdamut.vale"], "assist", 42)
    def test_unsafefast_lambdamut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambdamut.vale"], "unsafe-fast", 42)
    # def test_resilientv4_lambdamut(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambdamut.vale"], "resilient-v4", 42)
    def test_resilientv3_lambdamut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambdamut.vale"], "resilient-v3", 42)
    def test_naiverc_lambdamut(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/lambdas/lambdamut.vale"], "naive-rc", 42)

    def test_assist_strprint(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strprint.vale"], "assist", 42)
    def test_unsafefast_strprint(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strprint.vale"], "unsafe-fast", 42)
    # def test_resilientv4_strprint(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strprint.vale"], "resilient-v4", 42)
    def test_resilientv3_strprint(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strprint.vale"], "resilient-v3", 42)
    def test_naiverc_strprint(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strprint.vale"], "naive-rc", 42)

    def test_assist_inttostr(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/inttostr.vale"], "assist", 4)
    def test_unsafefast_inttostr(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/inttostr.vale"], "unsafe-fast", 4)
    # def test_resilientv4_inttostr(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/inttostr.vale"], "resilient-v4", 4)
    def test_resilientv3_inttostr(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/inttostr.vale"], "resilient-v3", 4)
    def test_naiverc_inttostr(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/inttostr.vale"], "naive-rc", 4)

    def test_assist_nestedif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/nestedif.vale"], "assist", 42)
    def test_unsafefast_nestedif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/nestedif.vale"], "unsafe-fast", 42)
    # def test_resilientv4_nestedif(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/nestedif.vale"], "resilient-v4", 42)
    def test_resilientv3_nestedif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/nestedif.vale"], "resilient-v3", 42)
    def test_naiverc_nestedif(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/nestedif.vale"], "naive-rc", 42)

    def test_assist_unstackifyret(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unstackifyret.vale"], "assist", 42)
    def test_unsafefast_unstackifyret(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unstackifyret.vale"], "unsafe-fast", 42)
    # def test_resilientv4_unstackifyret(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unstackifyret.vale"], "resilient-v4", 42)
    def test_resilientv3_unstackifyret(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unstackifyret.vale"], "resilient-v3", 42)
    def test_naiverc_unstackifyret(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unstackifyret.vale"], "naive-rc", 42)

    def test_assist_swaprsamutdestroy(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/swaprsamutdestroy.vale"], "assist", 42)
    def test_unsafefast_swaprsamutdestroy(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/swaprsamutdestroy.vale"], "unsafe-fast", 42)
    # def test_resilientv4_swaprsamutdestroy(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/swaprsamutdestroy.vale"], "resilient-v4", 42)
    def test_resilientv3_swaprsamutdestroy(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/swaprsamutdestroy.vale"], "resilient-v3", 42)
    def test_naiverc_swaprsamutdestroy(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/arrays/swaprsamutdestroy.vale"], "naive-rc", 42)

    def test_assist_downcastConstraintSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintSuccessful.vale"], "assist", 42)
    def test_unsafefast_downcastConstraintSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintSuccessful.vale"], "unsafe-fast", 42)
    # def test_resilientv4_downcastConstraintSuccessful(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintSuccessful.vale"], "resilient-v4", 42)
    def test_resilientv3_downcastConstraintSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintSuccessful.vale"], "resilient-v3", 42)
    def test_naiverc_downcastConstraintSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintSuccessful.vale"], "naive-rc", 42)

    def test_assist_downcastConstraintFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintFailed.vale"], "assist", 42)
    def test_unsafefast_downcastConstraintFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintFailed.vale"], "unsafe-fast", 42)
    # def test_resilientv4_downcastConstraintFailed(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintFailed.vale"], "resilient-v4", 42)
    def test_resilientv3_downcastConstraintFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintFailed.vale"], "resilient-v3", 42)
    def test_naiverc_downcastConstraintFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastConstraintFailed.vale"], "naive-rc", 42)

    def test_assist_downcastOwningSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningSuccessful.vale"], "assist", 42)
    def test_unsafefast_downcastOwningSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningSuccessful.vale"], "unsafe-fast", 42)
    # def test_resilientv4_downcastOwningSuccessful(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningSuccessful.vale"], "resilient-v4", 42)
    def test_resilientv3_downcastOwningSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningSuccessful.vale"], "resilient-v3", 42)
    def test_naiverc_downcastOwningSuccessful(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningSuccessful.vale"], "naive-rc", 42)

    def test_assist_downcastOwningFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningFailed.vale"], "assist", 42)
    def test_unsafefast_downcastOwningFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningFailed.vale"], "unsafe-fast", 42)
    # def test_resilientv4_downcastOwningFailed(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningFailed.vale"], "resilient-v4", 42)
    def test_resilientv3_downcastOwningFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningFailed.vale"], "resilient-v3", 42)
    def test_naiverc_downcastOwningFailed(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/downcast/downcastOwningFailed.vale"], "naive-rc", 42)

    def test_assist_unreachablemoot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unreachablemoot.vale"], "assist", 42)
    def test_unsafefast_unreachablemoot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unreachablemoot.vale"], "unsafe-fast", 42)
    # def test_resilientv4_unreachablemoot(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unreachablemoot.vale"], "resilient-v4", 42)
    def test_resilientv3_unreachablemoot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unreachablemoot.vale"], "resilient-v3", 42)
    def test_naiverc_unreachablemoot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/unreachablemoot.vale"], "naive-rc", 42)

    def test_assist_panic(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panic.vale"], "assist", 1)
    def test_unsafefast_panic(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panic.vale"], "unsafe-fast", 1)
    # def test_resilientv4_panic(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panic.vale"], "resilient-v4", 1)
    def test_resilientv3_panic(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panic.vale"], "resilient-v3", 1)
    def test_naiverc_panic(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panic.vale"], "naive-rc", 1)

    def test_assist_panicnot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panicnot.vale"], "assist", 42)
    def test_unsafefast_panicnot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panicnot.vale"], "unsafe-fast", 42)
    # def test_resilientv4_panicnot(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panicnot.vale"], "resilient-v4", 42)
    def test_resilientv3_panicnot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panicnot.vale"], "resilient-v3", 42)
    def test_naiverc_panicnot(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/panicnot.vale"], "naive-rc", 42)

    def test_assist_nestedblocks(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/nestedblocks.vale"], "assist", 42)
    def test_unsafefast_nestedblocks(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/nestedblocks.vale"], "unsafe-fast", 42)
    # def test_resilientv4_nestedblocks(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/nestedblocks.vale"], "resilient-v4", 42)
    def test_resilientv3_nestedblocks(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/nestedblocks.vale"], "resilient-v3", 42)
    def test_naiverc_nestedblocks(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/nestedblocks.vale"], "naive-rc", 42)

    def test_assist_weakDropThenLockStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockStruct.vale"], "assist", 42)
    def test_unsafefast_weakDropThenLockStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockStruct.vale"], "unsafe-fast", 42)
    # def test_resilientv4_weakDropThenLockStruct(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockStruct.vale"], "resilient-v4", 42)
    def test_resilientv3_weakDropThenLockStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockStruct.vale"], "resilient-v3", 42)
    def test_naiverc_weakDropThenLockStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockStruct.vale"], "naive-rc", 42)

    def test_assist_weakLockWhileLiveStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveStruct.vale"], "assist", 7)
    def test_unsafefast_weakLockWhileLiveStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveStruct.vale"], "unsafe-fast", 7)
    # def test_resilientv4_weakLockWhileLiveStruct(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveStruct.vale"], "resilient-v4", 7)
    def test_resilientv3_weakLockWhileLiveStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveStruct.vale"], "resilient-v3", 7)
    def test_naiverc_weakLockWhileLiveStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveStruct.vale"], "naive-rc", 7)

    def test_assist_weakFromLocalCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefStruct.vale"], "assist", 7)
    def test_unsafefast_weakFromLocalCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefStruct.vale"], "unsafe-fast", 7)
    # def test_resilientv4_weakFromLocalCRefStruct(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefStruct.vale"], "resilient-v4", 7)
    def test_resilientv3_weakFromLocalCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefStruct.vale"], "resilient-v3", 7)
    def test_naiverc_weakFromLocalCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefStruct.vale"], "naive-rc", 7)

    def test_assist_weakFromCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefStruct.vale"], "assist", 7)
    def test_unsafefast_weakFromCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefStruct.vale"], "unsafe-fast", 7)
    # def test_resilientv4_weakFromCRefStruct(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefStruct.vale"], "resilient-v4", 7)
    def test_resilientv3_weakFromCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefStruct.vale"], "resilient-v3", 7)
    def test_naiverc_weakFromCRefStruct(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefStruct.vale"], "naive-rc", 7)

    def test_assist_loadFromWeakable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/loadFromWeakable.vale"], "assist", 7)
    def test_unsafefast_loadFromWeakable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/loadFromWeakable.vale"], "unsafe-fast", 7)
    # def test_resilientv4_loadFromWeakable(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/loadFromWeakable.vale"], "resilient-v4", 7)
    def test_resilientv3_loadFromWeakable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/loadFromWeakable.vale"], "resilient-v3", 7)
    def test_naiverc_loadFromWeakable(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/loadFromWeakable.vale"], "naive-rc", 7)

    def test_assist_weakDropThenLockInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockInterface.vale"], "assist", 42)
    def test_unsafefast_weakDropThenLockInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockInterface.vale"], "unsafe-fast", 42)
    # def test_resilientv4_weakDropThenLockInterface(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockInterface.vale"], "resilient-v4", 42)
    def test_resilientv3_weakDropThenLockInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockInterface.vale"], "resilient-v3", 42)
    def test_naiverc_weakDropThenLockInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/dropThenLockInterface.vale"], "naive-rc", 42)

    def test_assist_weakLockWhileLiveInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveInterface.vale"], "assist", 7)
    def test_unsafefast_weakLockWhileLiveInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveInterface.vale"], "unsafe-fast", 7)
    # def test_resilientv4_weakLockWhileLiveInterface(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveInterface.vale"], "resilient-v4", 7)
    def test_resilientv3_weakLockWhileLiveInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveInterface.vale"], "resilient-v3", 7)
    def test_naiverc_weakLockWhileLiveInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/lockWhileLiveInterface.vale"], "naive-rc", 7)

    def test_assist_weakFromLocalCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefInterface.vale"], "assist", 7)
    def test_unsafefast_weakFromLocalCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefInterface.vale"], "unsafe-fast", 7)
    # def test_resilientv4_weakFromLocalCRefInterface(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefInterface.vale"], "resilient-v4", 7)
    def test_resilientv3_weakFromLocalCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefInterface.vale"], "resilient-v3", 7)
    def test_naiverc_weakFromLocalCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromLocalCRefInterface.vale"], "naive-rc", 7)

    def test_assist_weakFromCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefInterface.vale"], "assist", 7)
    def test_unsafefast_weakFromCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefInterface.vale"], "unsafe-fast", 7)
    # def test_resilientv4_weakFromCRefInterface(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefInterface.vale"], "resilient-v4", 7)
    def test_resilientv3_weakFromCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefInterface.vale"], "resilient-v3", 7)
    def test_naiverc_weakFromCRefInterface(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/weakFromCRefInterface.vale"], "naive-rc", 7)

    def test_assist_weakSelfMethodCallWhileLive(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodWhileLive.vale"], "assist", 42)
    def test_unsafefast_weakSelfMethodCallWhileLive(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodWhileLive.vale"], "unsafe-fast", 42)
    # def test_resilientv4_weakSelfMethodCallWhileLive(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodWhileLive.vale"], "resilient-v4", 42)
    def test_resilientv3_weakSelfMethodCallWhileLive(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodWhileLive.vale"], "resilient-v3", 42)
    def test_naiverc_weakSelfMethodCallWhileLive(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodWhileLive.vale"], "naive-rc", 42)

    def test_assist_weakSelfMethodCallAfterDrop(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodAfterDrop.vale"], "assist", 0)
    def test_unsafefast_weakSelfMethodCallAfterDrop(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodAfterDrop.vale"], "unsafe-fast", 0)
    # def test_resilientv4_weakSelfMethodCallAfterDrop(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodAfterDrop.vale"], "resilient-v4", 0)
    def test_resilientv3_weakSelfMethodCallAfterDrop(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodAfterDrop.vale"], "resilient-v3", 0)
    def test_naiverc_weakSelfMethodCallAfterDrop(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/weaks/callWeakSelfMethodAfterDrop.vale"], "naive-rc", 0)

    # def test_assist_tupleretextern(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/tupleretextern"], "assist", 42)
    # def test_unsafefast_tupleretextern(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/tupleretextern"], "unsafe-fast", 42)
    # # def test_resilientv4_tupleretextern(self) -> None:
    # #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/tupleretextern"], "resilient-v4", 42)
    # def test_resilientv3_tupleretextern(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/tupleretextern"], "resilient-v3", 42)
    # def test_naiverc_tupleretextern(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/tupleretextern"], "naive-rc", 42)





    def test_assist_interfaceimmreturnextern(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfaceimmreturnextern"], "assist", 42)
    # def test_unsafefast_interfaceimmreturnextern(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfaceimmreturnextern"], "unsafe-fast", 42)
    # def test_resilientv4_interfaceimmreturnextern(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfaceimmreturnextern"], "resilient-v4", 42)
    def test_resilientv3_interfaceimmreturnextern(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfaceimmreturnextern"], "resilient-v3", 42)
    # def test_naiverc_interfaceimmreturnextern(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfaceimmreturnextern"], "naive-rc", 42)

    def test_assist_interfaceimmreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfaceimmreturnexport"], "assist", 42)
    # def test_unsafefast_interfaceimmreturnexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfaceimmreturnexport"], "unsafe-fast", 42)
    # def test_resilientv4_interfaceimmreturnexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfaceimmreturnexport"], "resilient-v4", 42)
    def test_resilientv3_interfaceimmreturnexport(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfaceimmreturnexport"], "resilient-v3", 42)
    # def test_naiverc_interfaceimmreturnexport(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/interfaceimmreturnexport"], "naive-rc", 42)



    def test_assist_strlen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strlen.vale"], "assist", 12)
    def test_unsafefast_strlen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strlen.vale"], "unsafe-fast", 12)
    # def test_resilientv4_strlen(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strlen.vale"], "resilient-v4", 12)
    def test_resilientv3_strlen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strlen.vale"], "resilient-v3", 12)
    def test_naiverc_strlen(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/strings/strlen.vale"], "naive-rc", 12)

    # no assist test: Cant get an invalid access in assist mode, a constraint ref catches it first
    def test_unsafefast_invalidaccess(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/invalidaccess.vale"], "unsafe-fast", 14)
    # def test_resilientv4_invalidaccess(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/invalidaccess.vale"], "resilient-v4", -11)
    def test_resilientv3_invalidaccess(self) -> None:
        self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/invalidaccess.vale"], "resilient-v3", -11)
    # def test_naiverc_invalidaccess(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/invalidaccess.vale"], "naive-rc", 255)

    # def test_assist_neverif(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/neverif.vale"], "assist", 42)
    # def test_unsafefast_neverif(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/neverif.vale"], "unsafe-fast", 42)
    # # def test_resilientv4_neverif(self) -> None:
    # #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/neverif.vale"], "resilient-v4", 42)
    # def test_resilientv3_neverif(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/neverif.vale"], "resilient-v3", 42)
    # def test_naiverc_neverif(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/if/neverif.vale"], "naive-rc", 42)

    # def test_assist_tupleparamextern(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/tupleparamextern"], "assist", 42)
    # def test_unsafefast_tupleparamextern(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/tupleparamextern"], "unsafe-fast", 42)
    # # def test_resilientv4_tupleparamextern(self) -> None:
    # #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/tupleparamextern"], "resilient-v4", 42)
    # def test_resilientv3_tupleparamextern(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/tupleparamextern"], "resilient-v3", 42)
    # def test_naiverc_tupleparamextern(self) -> None:
    #     self.compile_and_execute_and_expect_return_code([PATH_TO_SAMPLES + "programs/externs/tupleparamextern"], "naive-rc", 42)


if __name__ == '__main__':
    unittest.main()
